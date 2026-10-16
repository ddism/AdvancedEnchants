package com.advancedenchants.storage;

import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.util.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔数据的物品存储层。
 *
 * 权威数据存于 PersistentDataContainer：key = {@code advancedenchants:<附魔ID>}，value = 等级。
 * 优点：重启/跨服复制/潜影盒/掉落物都不丢，且不依赖服务端自定义附魔注册是否成功。
 * 同时负责把数据同步到 ItemMeta 附魔（供 tooltip 与光效）与 Lore。
 */
public final class ItemTags {

    /** 附魔 ID -> 定义 的解析函数，由主类在启动完成后注入。 */
    private static java.util.function.Function<String, EnchantDef> resolver = id -> null;

    private static NamespacedKey soulsKey;
    private static NamespacedKey useCountKey;
    private static int maxPerItem = 12;

    private ItemTags() {
    }

    public static void init(Plugin plugin, int maxPerItemConfig,
                            java.util.function.Function<String, EnchantDef> defResolver) {
        soulsKey = new NamespacedKey(plugin, "souls");
        useCountKey = new NamespacedKey(plugin, "use_count");
        maxPerItem = Math.max(1, maxPerItemConfig);
        resolver = defResolver;
    }

    public static NamespacedKey soulsKey() {
        return soulsKey;
    }

    public static int maxPerItem() {
        return maxPerItem;
    }

    public static NamespacedKey keyOf(String id) {
        return NamespacedKey.fromString("advancedenchants:" + id);
    }

    // ------------------------------------------------------------- 读

    /** 读取物品上的全部自定义附魔（保持插入顺序，顺便清理失效 ID）。 */
    public static Map<String, Integer> read(ItemStack item) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return out;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!"advancedenchants".equals(key.getNamespace())) {
                continue;
            }
            String id = key.getKey();
            if (id.equals("souls") || id.equals("use_count")) {
                continue;
            }
            Integer level = pdc.get(key, PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                out.put(id, level);
            }
        }
        return out;
    }

    private static ItemMeta metaOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta();
    }

    public static int level(ItemStack item, String id) {
        return read(item).getOrDefault(id, 0);
    }

    public static boolean has(ItemStack item, String id) {
        return level(item, id) > 0;
    }

    public static boolean hasAny(ItemStack item) {
        return !read(item).isEmpty();
    }

    public static int souls(ItemStack item) {
        return intTag(item, soulsKey);
    }

    public static int useCount(ItemStack item) {
        return intTag(item, useCountKey);
    }

    private static int intTag(ItemStack item, NamespacedKey key) {
        ItemMeta meta = metaOf(item);
        if (meta == null || key == null) {
            return 0;
        }
        Integer v = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    // ------------------------------------------------------------- 写

    /** 设置等级；level<=0 表示移除。自动同步 ItemMeta 附魔与 Lore。 */
    public static boolean put(ItemStack item, EnchantDef def, int level) {
        if (item == null || def == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey key = keyOf(def.id());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (level <= 0) {
            pdc.remove(key);
        } else {
            boolean existing = has(item, def.id());
            if (!existing && read(item).size() >= maxPerItem) {
                return false;
            }
            pdc.set(key, PersistentDataType.INTEGER, Math.min(level, def.maxLevel()));
        }
        item.setItemMeta(meta);
        syncEnchantment(item, def, level);
        LoreRenderer.render(item, def);
        return true;
    }

    public static boolean remove(ItemStack item, EnchantDef def) {
        return put(item, def, 0);
    }

    /** 清空物品上的全部自定义附魔数据（用于磨刀石/清除指令）。 */
    public static void clearAll(ItemStack item) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<NamespacedKey> keys = new ArrayList<>(pdc.getKeys());
        for (NamespacedKey key : keys) {
            if ("advancedenchants".equals(key.getNamespace())) {
                pdc.remove(key);
            }
        }
        item.setItemMeta(meta);
    }

    public static void setSouls(ItemStack item, int value) {
        mutateInt(item, soulsKey, value);
    }

    public static void addSouls(ItemStack item, int delta) {
        mutateInt(item, soulsKey, souls(item) + delta);
    }

    public static void setUseCount(ItemStack item, int value) {
        mutateInt(item, useCountKey, value);
    }

    private static void mutateInt(ItemStack item, NamespacedKey key, int value) {
        if (item == null || item.getType().isAir() || key == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (value == 0) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        }
        item.setItemMeta(meta);
    }

    /**
     * 同步到 ItemMeta 附魔。若服务端未成功注册该自定义附魔，则跳过（Lore 仍可展示）。
     */
    public static void syncEnchantment(ItemStack item, EnchantDef def, int level) {
        ItemMeta meta = metaOf(item);
        if (meta == null) {
            return;
        }
        Enchantment ench = com.advancedenchants.util.VersionSafe.enchantment(keyOf(def.id()));
        if (ench == null) {
            return;
        }
        try {
            if (level <= 0) {
                meta.removeEnchant(ench);
            } else {
                meta.addEnchant(ench, level, true);
            }
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
            // 忽略：Lore 已可读
        }
    }

    /** 计算 {value} 用：第一个动作在当前等级的数值。 */
    public static double value(EnchantDef def, int level) {
        return com.advancedenchants.model.Levels.scaled(firstAction(def), level, def.maxLevel());
    }

    private static Map<String, Object> firstAction(EnchantDef def) {
        for (List<com.advancedenchants.model.TriggerDef> list : def.triggers().values()) {
            for (com.advancedenchants.model.TriggerDef td : list) {
                for (com.advancedenchants.model.ActionDef a : td.actions()) {
                    return a.data();
                }
            }
        }
        return Map.of();
    }

    /** 等级文本（罗马数字，用于 Lore）。 */
    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    /** 调试输出。 */
    public static String describe(ItemStack item) {
        Map<String, Integer> data = read(item);
        if (data.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        data.forEach((id, lv) -> sb.append(id).append(" Lv").append(lv).append(' '));
        return sb.toString().trim();
    }

    public static java.util.function.Function<String, EnchantDef> resolver() {
        return resolver;
    }
}
