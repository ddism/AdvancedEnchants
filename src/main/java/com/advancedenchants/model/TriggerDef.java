package com.advancedenchants.model;

import java.util.List;
import java.util.regex.Pattern;

/** 单个触发器块的定义。 */
public record TriggerDef(
        Trigger trigger,
        long cooldownMillis,
        double chance,
        double chancePerLevel,
        List<CondDef> conditions,
        List<ActionDef> actions
) {

    /** 常量字符串形式（用于日志）。 */
    public String raw() {
        return trigger == null ? "UNKNOWN" : trigger.name();
    }

    public double effectiveChance(int level) {
        return Math.max(0.0, Math.min(100.0, chance + chancePerLevel * (level - 1)));
    }

    public boolean hasCooldown() {
        return cooldownMillis > 0;
    }

    public static final Pattern UNUSED = Pattern.compile("^$");

    public static List<CondDef> emptyConditions() {
        return List.of();
    }
}
