package com.advancedenchants.core;

import com.advancedenchants.model.ActionDef;
import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.model.Trigger;
import com.advancedenchants.model.TriggerDef;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

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
}
