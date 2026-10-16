package com.advancedenchants.model;

import java.util.Locale;
import java.util.Set;

/** 附魔适用类型（对应 SCHEMA.md 2.1）。 */
public enum ApplyType {

    SWORD,
    AXE,
    TRIDENT,
    BOW,
    CROSSBOW,
    ARMOR,
    SHIELD,
    PICKAXE,
    SHOVEL,
    HOE,
    SHEARS,
    FISHING_ROD,
    ELYTRA,
    ALL;

    public static ApplyType parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ApplyType t : values()) {
            if (t.name().equals(key)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 依据物品类型解析其归属的所有 ApplyType（一把斧头同时是 AXE 与「工具」语义由配置决定）。
     */
    public static Set<ApplyType> of(org.bukkit.Material material) {
        return MaterialTypes.of(material);
    }
}
