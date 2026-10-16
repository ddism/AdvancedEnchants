package com.advancedenchants.model;

import java.util.List;
import java.util.Map;

/** 条件定义（对应 SCHEMA.md 第 4 节）。 */
public record CondDef(String type, Map<String, Object> data) {

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

    @SuppressWarnings("unchecked")
    public List<String> list(String key) {
        Object v = data.get(key);
        if (v instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        if (v == null) {
            return List.of();
        }
        return List.of(String.valueOf(v));
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }
}
