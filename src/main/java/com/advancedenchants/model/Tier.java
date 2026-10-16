package com.advancedenchants.model;

import java.util.Locale;

/** 附魔等级（稀有度）。 */
public enum Tier {

    COMMON("普通", "&7", 100),
    UNCOMMON("优秀", "&a", 55),
    RARE("稀有", "&9", 26),
    EPIC("史诗", "&5", 10),
    LEGENDARY("传说", "&6", 4),
    MYTHIC("神话", "&c", 1);

    private final String zh;
    private final String color;
    private final int defaultWeight;

    Tier(String zh, String color, int defaultWeight) {
        this.zh = zh;
        this.color = color;
        this.defaultWeight = defaultWeight;
    }

    public String zh() {
        return zh;
    }

    public String color() {
        return color;
    }

    /** 默认随机权重（可被配置的 rarity-weight 覆盖）。 */
    public int defaultWeight() {
        return defaultWeight;
    }

    public static Tier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMON;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (Tier t : values()) {
            if (t.name().equals(key) || t.zh.equals(raw.trim())) {
                return t;
            }
        }
        return COMMON;
    }
}
