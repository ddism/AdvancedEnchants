package com.advancedenchants.model;

import java.util.Locale;

/** 所有支持的触发器（对应 SCHEMA.md 第 3 节）。 */
public enum Trigger {

    ON_HIT,
    ON_KILL,
    ON_DAMAGED,
    ON_DEATH,
    ON_BLOCK_BREAK,
    ON_BLOCK_PLACE,
    ON_MINE_TICK,
    ON_SHOOT,
    ON_PROJECTILE_HIT,
    ON_FISH,
    ON_JUMP,
    ON_FALL_DAMAGE,
    ON_MOVE,
    ON_SNEAK,
    ON_BOW_CHARGE,
    ON_SHIELD_BLOCK,
    ON_INTERACT,
    ON_ANVIL_COMBINE,
    ON_ENCHANT,
    ON_EQUIP,
    ON_CONSUME,
    ON_SWAP_HAND,
    PASSIVE,
    ON_LOOT,
    ON_XP_GAIN,
    ON_DISABLE;

    public static Trigger parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (key.equals("SHIELD_BLOCK")) {
            key = "ON_SHIELD_BLOCK";
        }
        if (key.equals("XP_GAIN")) {
            key = "ON_XP_GAIN";
        }
        for (Trigger t : values()) {
            if (t.name().equals(key)) {
                return t;
            }
        }
        return null;
    }

    /** 该触发点是否需要周期性玩家扫描（PASSIVE 用）。 */
    public boolean isPassive() {
        return this == PASSIVE;
    }
}
