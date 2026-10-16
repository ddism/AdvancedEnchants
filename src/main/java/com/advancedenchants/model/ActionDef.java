package com.advancedenchants.model;

import java.util.Map;

/** 动作定义（对应 SCHEMA.md 第 5 节）。 */
public record ActionDef(String type, Map<String, Object> data) {

    public double d(String key, double def) {
        Object v = data.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public int i(String key, int def) {
        return (int) Math.round(d(key, def));
    }

    public String s(String key, String def) {
        Object v = data.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public boolean bool(String key, boolean def) {
        Object v = data.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return def;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }
}
