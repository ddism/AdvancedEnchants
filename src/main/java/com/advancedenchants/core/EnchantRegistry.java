package com.advancedenchants.core;

import com.advancedenchants.model.ActionDef;
import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.model.Trigger;
import com.advancedenchants.model.TriggerDef;
import com.advancedenchants.storage.ItemTags;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 附魔注册表：YAML 定义的权威容器 + 服务端注册结果。 */
public final class EnchantRegistry {

    public EnchantRegistry() {
    }

    private final Map<String, EnchantDef> byId = new LinkedHashMap<>();
    private final Map<NamespacedKey, EnchantDef> byKey = new LinkedHashMap<>();

    /** 已成功注册进服务端 Registry（false 表示降级为纯 PDC 模式）。 */
    private boolean serverRegistration;
    private final Set<String> registrationFailed = new java.util.LinkedHashSet<>();

    public boolean serverRegistration() {
        return serverRegistration;
    }

    public void setServerRegistration(boolean value) {
        this.serverRegistration = value;
    }

    public Set<String> registrationFailed() {
        return Collections.unmodifiableSet(registrationFailed);
    }

    public void markRegistrationFailed(String id) {
        registrationFailed.add(id);
    }

    public Set<String> knownActions() {
        return EnchantLoader.Actions.KNOWN;
    }

    public Set<String> knownConditions() {
        return EnchantLoader.Conditions.KNOWN;
    }

    /** 某个附魔在指定触发点上第一个动作的等级数值（用于 {value} 占位符与描述）。 */
    public static double primaryValue(EnchantDef def, int level) {
        for (List<TriggerDef> list : def.triggers().values()) {
            for (TriggerDef td : list) {
                for (ActionDef a : td.actions()) {
                    return Conditions.scaled(a.data(), level, def.maxLevel());
                }
            }
        }
        return 0;
    }
    public static boolean hasTrigger(EnchantDef def, Trigger trigger) {
        List<TriggerDef> list = def.triggers().get(trigger);
        return list != null && !list.isEmpty();
    }

    /** 是否任意已注册附魔在指定触发点上定义了动作（用于跳过不需要的事件处理）。 */
    public boolean anyDefinesTrigger(Trigger trigger) {
        for (EnchantDef def : byId.values()) {
            if (hasTrigger(def, trigger)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只保留「在指定触发点上定义了动作」的物品。
     *
     * 用途：ON_DAMAGED 与 ON_SHIELD_BLOCK 会共用同一批装备（盾牌同时在副手与盔甲列表里），
     * 若不做区分，同一件附魔会在一次伤害里被结算两次（减伤翻倍）。
     */
    public List<ItemStack> filterItemsByTrigger(List<ItemStack> items, Trigger trigger) {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            Map<String, Integer> enchants = ItemTags.read(item);
            for (String id : enchants.keySet()) {
                EnchantDef def = byId.get(id);
                if (def != null && hasTrigger(def, trigger)) {
                    out.add(item);
                    break;
                }
            }
        }
        return out;
    }

    /** 是否任意已注册附魔使用了该动作类型（用于跳过完全不需要的事件处理）。 */
    public boolean anyUsesAction(String actionType) {
        for (EnchantDef def : byId.values()) {
            for (List<TriggerDef> list : def.triggers().values()) {
                for (TriggerDef td : list) {
                    for (ActionDef a : td.actions()) {
                        if (a.type().equals(actionType)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void register(EnchantDef def) {
        EnchantDef previous = byId.put(def.id(), def);
        if (previous != null) {
            byKey.remove(EnchantRegistry.keyOf(def.id()));
        }
        byKey.put(EnchantRegistry.keyOf(def.id()), def);
    }

    public boolean remove(String id) {
        EnchantDef removed = byId.remove(id);
        if (removed != null) {
            byKey.remove(keyOf(id));
            return true;
        }
        return false;
    }

    public void clear() {
        byId.clear();
        byKey.clear();
    }

    public EnchantDef get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public EnchantDef get(NamespacedKey key) {
        return key == null ? null : byKey.get(key);
    }

    public EnchantDef get(Enchantment enchantment) {
        if (enchantment == null) {
            return null;
        }
        EnchantDef byNamespaced = byKey.get(enchantment.getKey());
        if (byNamespaced != null) {
            return byNamespaced;
        }
        return get(enchantment.getKey().getKey());
    }

    public Collection<EnchantDef> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    /** 按等级分组的统计（用于 /ae list 与启动日志）。 */
    public Map<String, Integer> countByTier() {
        Map<String, Integer> out = new TreeMap<>();
        for (EnchantDef def : byId.values()) {
            out.merge(def.tier().name(), 1, Integer::sum);
        }
        return out;
    }

    public Map<String, List<String>> idsByTier() {
        Map<String, List<String>> out = new TreeMap<>();
        for (EnchantDef def : byId.values()) {
            out.computeIfAbsent(def.tier().name(), k -> new ArrayList<>()).add(def.id());
        }
        return out;
    }

    public static NamespacedKey keyOf(String id) {
        return NamespacedKey.fromString("advancedenchants:" + id.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 把物品上的自定义附魔等级钳制到各附魔定义的 max-level，就地修改并返回是否有改动。
     *
     * 为什么需要：原版流程（铁砧叠加 / 村民交易 / 附魔台 / 战利品）走的是
     * {@code ItemMeta#addEnchant(ench, level, true)} 或直接写入物品，
     * 都无视附魔自己的 max-level —— 玩家能拿到超过配置上限的等级。
     */
    public boolean clampLevels(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Map<String, Integer> enchants = ItemTags.read(item);
        if (enchants.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            EnchantDef def = byId.get(entry.getKey());
            if (def == null) {
                continue;
            }
            int max = def.maxLevel();
            if (entry.getValue() > max && ItemTags.put(item, def, max)) {
                changed = true;
            }
        }
        return changed;
    }
}
