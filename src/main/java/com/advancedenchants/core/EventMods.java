package com.advancedenchants.core;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 一次事件中所有附魔动作累积出来的修改量。
 * 动作只往这里写数值/开关，事件监听器在全部附魔执行完后统一 apply 到 Bukkit 事件上，
 * 这样多个附魔的加成可以正确叠加，也不会互相覆盖。
 */
public final class EventMods {

    private final Map<String, Double> numbers = new LinkedHashMap<>();
    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);

    public enum Flag {
        CANCEL_EVENT,
        DODGE,
        SURVIVE,
        EXTINGUISH,
        IMMUNE_PROJECTILE,
        PREVENT_DEATH_DROP,
        ATTACK_SPEED,
        NO_FALL_DAMAGE,
        NO_FIRE_DAMAGE,
        SOULBOUND
    }

    public double get(String key, double def) {
        return numbers.getOrDefault(key, def);
    }

    /** 无默认值时返回 0（便于直接读取 dmg.cap 之类的可选值）。 */
    public double get(String key) {
        return numbers.getOrDefault(key, 0.0);
    }

    public void set(String key, double value) {
        numbers.put(key, value);
    }

    /** 乘算叠加（多个附魔的倍率相乘）。 */
    public void multiply(String key, double factor) {
        numbers.merge(key, factor, (a, b) -> a * b);
    }

    /** 加算叠加。 */
    public void add(String key, double value) {
        numbers.merge(key, value, Double::sum);
    }

    public boolean has(String key) {
        return numbers.containsKey(key);
    }

    public void flag(Flag flag) {
        flags.add(flag);
    }

    public boolean flagged(Flag flag) {
        return flags.contains(flag);
    }

    public Set<Flag> flags() {
        return flags;
    }

    /** 只读视图：监听器用它遍历 dmg.reduce.* 等带命名空间前缀的修正量。 */
    public Map<String, Double> numbers() {
        return java.util.Collections.unmodifiableMap(numbers);
    }

    // ------------------------------------------------------------- 应用

    /**
     * 把修改应用到一次伤害上。
     *
     * @param raw 事件原始伤害
     * @return 处理后的伤害（未做最终校验）
     */
    public double applyDamage(double raw) {
        double dmg = raw;
        for (Map.Entry<String, Double> e : numbers.entrySet()) {
            if (e.getKey().startsWith("dmg.mult.")) {
                dmg *= e.getValue();
            }
        }
        for (Map.Entry<String, Double> e : numbers.entrySet()) {
            if (e.getKey().startsWith("dmg.add.")) {
                dmg += e.getValue();
            }
        }
        // 玩家自身承受的伤害修正（如玻璃大炮的 target: SELF）
        for (Map.Entry<String, Double> e : numbers.entrySet()) {
            if (e.getKey().startsWith("mydmg.mult.")) {
                dmg *= e.getValue();
            }
        }
        for (Map.Entry<String, Double> e : numbers.entrySet()) {
            if (e.getKey().startsWith("mydmg.add.")) {
                dmg += e.getValue();
            }
        }
        if (numbers.containsKey("dmg.true")) {
            dmg += numbers.get("dmg.true");
        }
        if (flagged(Flag.DODGE)) {
            return 0;
        }
        // 减伤：多个附魔各自乘算叠加（1-0.3 与 1-0.2 → ×0.56）
        for (Map.Entry<String, Double> e : numbers.entrySet()) {
            if (e.getKey().startsWith("dmg.reduce.")) {
                dmg *= Math.max(0, 1 - Math.min(1.0, e.getValue()));
            }
        }
        double flatReduce = get("dmg.reduce.flat", 0);
        if (flatReduce > 0) {
            dmg = Math.max(0, dmg - flatReduce);
        }
        double pierce = get("dmg.pierce", 0);
        if (pierce > 0) {
            // 忽略护甲：按比例补偿一部分伤害
            dmg *= 1 + Math.min(1.0, pierce) * 0.5;
        }
        if (numbers.containsKey("dmg.cap")) {
            dmg = Math.min(dmg, numbers.get("dmg.cap"));
        }
        return Math.max(0, dmg);
    }

    /** 是否需要修改伤害事件本身。 */
    public boolean touchesDamage() {
        for (String key : numbers.keySet()) {
            if (key.startsWith("dmg.") || key.startsWith("mydmg.")) {
                return true;
            }
        }
        return flagged(Flag.DODGE) || flagged(Flag.SURVIVE);
    }

    /** 调试输出。 */
    public String describe() {
        return numbers + " " + flags;
    }
}
