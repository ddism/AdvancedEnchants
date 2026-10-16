package com.advancedenchants.model;

import java.util.Locale;
import java.util.Map;

/** 等级数值公式（SCHEMA.md 2.2）。 */
public final class Levels {

    private Levels() {
    }

    /**
     * scaled = (base + perLevel * (level - 1)) * modeFactor，最后按 max 钳制、不小于 0。
     */
    public static double scaled(Map<String, Object> data, int level, int maxLevel) {
        double base = num(data.get("base"), 0);
        double perLevel = num(data.get("per-level"), 0);
        int lv = Math.max(1, level);
        double value = base + perLevel * (lv - 1);

        String mode = data.get("scale") == null ? "LINEAR" : String.valueOf(data.get("scale")).trim().toUpperCase(Locale.ROOT);
        switch (mode) {
            case "QUADRATIC" -> value *= 1.0 + 0.25 * (lv - 1);
            case "EXPONENTIAL" -> value *= Math.pow(1.6, lv - 1);
            case "DIMINISHING" -> value *= 1.0 + 0.6 * (lv - 1) / Math.max(1, maxLevel);
            default -> {
                // LINEAR
            }
        }
        if (data.containsKey("max")) {
            value = Math.min(value, num(data.get("max"), value));
        }
        return Math.max(0.0, value);
    }

    /** 支持 base/per-level/max 的通用取值（用于 max/duration 等非钳制字段）。 */
    public static double plain(Map<String, Object> data, String key, int level, double def) {
        double base = num(data.get(key), def);
        double per = num(data.get(key + "-per-level"), 0);
        return Math.max(0.0, base + per * (Math.max(1, level) - 1));
    }

    public static double num(Object value, double def) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
