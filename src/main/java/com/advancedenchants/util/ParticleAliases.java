package com.advancedenchants.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 粒子名称的跨版本别名表。
 *
 * 1.20.5 起 Bukkit 重命名了一批 Particle 常量（如 EXPLOSION_NORMAL -> POOF）。
 * 配置里写「任一版本的官方名」都能解析：先试原名，失败后经 {@link #alias} 得到对端名字再试。
 * 两张表互为逆映射。
 */
public final class ParticleAliases {

    private static final Map<String, String> ALIAS = build();

    private ParticleAliases() {
    }

    /** 返回对端版本的名字；没有别名时返回 null。 */
    public static String alias(String name) {
        if (name == null) {
            return null;
        }
        return ALIAS.get(name.trim().toUpperCase(Locale.ROOT));
    }

    /** 该名字是否是 1.20.4 时代的旧名（用于提示用户写的是旧名）。 */
    public static boolean isLegacy(String name) {
        String upper = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_HUGE", "VILLAGER_HAPPY", "VILLAGER_ANGRY",
                 "REDSTONE", "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_INSTANT", "SPELL_WITCH", "WATER_BUBBLE",
                 "WATER_SPLASH", "WATER_WAKE", "SMOKE_NORMAL", "SMOKE_LARGE", "FIREWORKS_SPARK", "TOTEM",
                 "SNOWBALL", "SLIME", "BLOCK_CRACK", "BLOCK_DUST", "DRIP_LAVA", "DRIP_WATER", "CRIT_MAGIC",
                 "ENCHANTMENT_TABLE", "MOB_APPEARANCE", "FOOTSTEP" -> true;
            default -> false;
        };
    }

    private static Map<String, String> build() {
        Map<String, String> map = new HashMap<>();
        pair(map, "EXPLOSION_NORMAL", "POOF");
        pair(map, "EXPLOSION_LARGE", "EXPLOSION");
        pair(map, "EXPLOSION_HUGE", "EXPLOSION_EMITTER");
        pair(map, "VILLAGER_HAPPY", "HAPPY_VILLAGER");
        pair(map, "VILLAGER_ANGRY", "ANGRY_VILLAGER");
        pair(map, "REDSTONE", "DUST");
        pair(map, "SPELL_MOB", "ENTITY_EFFECT");
        pair(map, "SPELL_MOB_AMBIENT", "ENTITY_EFFECT");
        pair(map, "SPELL_INSTANT", "INSTANT_EFFECT");
        pair(map, "SPELL_WITCH", "WITCH");
        pair(map, "WATER_BUBBLE", "BUBBLE");
        pair(map, "WATER_SPLASH", "SPLASH");
        pair(map, "WATER_WAKE", "BUBBLE_POP");
        pair(map, "SMOKE_NORMAL", "SMOKE");
        pair(map, "SMOKE_LARGE", "LARGE_SMOKE");
        pair(map, "FIREWORKS_SPARK", "FIREWORK");
        pair(map, "TOTEM", "TOTEM_OF_UNDYING");
        pair(map, "SNOWBALL", "ITEM_SNOWBALL");
        pair(map, "SLIME", "ITEM_SLIME");
        // BLOCK 系列：1.20.5 把 BLOCK_CRACK / BLOCK_DUST 合并为 BLOCK。
        // 1.20.4 上没有 BLOCK，回退到 BLOCK_CRACK（方块破坏粒子更贴切）。
        // pair() 双向写入且同一个键只能写一次 —— 下面 put 覆盖 BLOCK 的落点，
        // 之后不允许再出现任何以 BLOCK 为「第二个参数」的 pair，否则会把落点改掉。
        pair(map, "BLOCK_CRACK", "BLOCK");
        pair(map, "BLOCK_DUST", "BLOCK");
        map.put("BLOCK", "BLOCK_CRACK");
        pair(map, "DRIP_LAVA", "DRIPPING_LAVA");
        pair(map, "DRIP_WATER", "DRIPPING_WATER");
        pair(map, "CRIT_MAGIC", "ENCHANTED_HIT");
        pair(map, "ENCHANTMENT_TABLE", "ENCHANT");
        pair(map, "MOB_APPEARANCE", "ELDER_GUARDIAN");
        // FOOTSTEP 是 1.20.4 独有、1.20.5 已删除的粒子：正向名保留，反向不回写 BLOCK
        map.put("FOOTSTEP", "BLOCK_CRACK");
        return Map.copyOf(map);
    }

    private static void pair(Map<String, String> map, String legacy, String modern) {
        map.put(legacy, modern);
        map.put(modern, legacy);
    }
}
