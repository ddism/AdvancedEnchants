package com.advancedenchants.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 版本适配层：AdvancedEnchants 运行在 Paper 1.20.4 ~ 1.21.x 上，
 * 而不同版本之间 Registry / Enchantment / PotionEffectType 的 API 有差异。
 * 所有可能变化的调用都收敛到本类，编译期不依赖任何被移除的符号。
 */
public final class VersionSafe {

    private static final Map<String, PotionEffectType> POTION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Enchantment> ENCHANT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object> ENUM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> POTION_ALIASES = buildPotionAliases();
    private static JavaPlugin plugin;

    /** 跨版本药水名映射（1.20.5 重命名了一批效果），双向映射以便任意一侧拼写都能解析。 */
    private static Map<String, String> buildPotionAliases() {
        Map<String, String> map = new java.util.HashMap<>();
        alias(map, "DAMAGE_RESISTANCE", "RESISTANCE");
        alias(map, "SLOW_DIGGING", "MINING_FATIGUE");
        alias(map, "JUMP", "JUMP_BOOST");
        alias(map, "CONFUSION", "NAUSEA");
        alias(map, "HARM", "INSTANT_DAMAGE");
        alias(map, "HEAL", "INSTANT_HEALTH");
        // 1.20.5 起：HASTE -> FAST_DIGGING 的反向命名；SLOWNESS -> SLOW
        alias(map, "HASTE", "FAST_DIGGING");
        alias(map, "SLOWNESS", "SLOW");
        return Map.copyOf(map);
    }

    private static void alias(Map<String, String> map, String a, String b) {
        map.put(a, b);
        map.put(b, a);
    }

    private VersionSafe() {
    }

    public static void init(JavaPlugin instance) {
        plugin = instance;
    }

    private static void warn(String msg) {
        if (plugin != null) {
            plugin.getLogger().warning(msg);
        }
    }

    // ------------------------------------------------------------------ registry

    /**
     * 兼容方式获取 Registry，避免在编译期依赖 1.20.4 已被移除的 Registry.EFFECT / ENCHANTMENT 常量。
     * 顺序：
     *   1) Bukkit.getRegistry(Class)（1.20.5+ 推荐 API，反射调用以兼容旧版本）；
     *   2) Registry 的静态字段（按版本名尝试），并用元素类型校验，避免返回错误的 registry。
     */
    @SuppressWarnings("unchecked")
    public static <T extends org.bukkit.Keyed> Registry<T> registry(Class<T> type) {
        try {
            Method m = org.bukkit.Bukkit.class.getMethod("getRegistry", Class.class);
            Object r = m.invoke(null, type);
            if (r instanceof Registry<?> reg) {
                return (Registry<T>) reg;
            }
        } catch (Throwable ignored) {
            // 旧版本没有 Bukkit#getRegistry
        }
        for (String fieldName : new String[]{"ENCHANTMENT", "EFFECT", "POTION_EFFECT_TYPE"}) {
            try {
                Field f = Registry.class.getField(fieldName);
                Object r = f.get(null);
                if (!(r instanceof Registry<?> reg)) {
                    continue;
                }
                Registry<T> cast = (Registry<T>) reg;
                // 元素类型校验：registry 为空或首元素类型匹配才接受
                for (T element : cast) {
                    return type.isInstance(element) ? cast : null;
                }
                return cast;
            } catch (Throwable ignored) {
                // 继续尝试下一个字段名
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ potion

    /** 药水效果解析：兼容新版 Registry 与旧版 getByName。 */
    public static PotionEffectType potion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (POTION_CACHE.containsKey(key)) {
            return POTION_CACHE.get(key);
        }
        PotionEffectType found = null;

        // 1) Registry 查找（1.20.5+ 命名空间键优先）
        try {
            NamespacedKey nk = key.contains(":") ? NamespacedKey.fromString(key.toLowerCase(Locale.ROOT))
                    : NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT));
            if (nk != null) {
                Registry<PotionEffectType> reg = registry(PotionEffectType.class);
                if (reg != null) {
                    found = reg.get(nk);
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) 旧版静态方法 getByName
        if (found == null) {
            try {
                Method m = PotionEffectType.class.getMethod("getByName", String.class);
                Object r = m.invoke(null, key);
                if (r instanceof PotionEffectType p) {
                    found = p;
                }
            } catch (Throwable ignored) {
            }
        }

        if (found == null) {
            // 3) 跨版本别名（1.20.5 重命名了一批药水：DAMAGE_RESISTANCE -> RESISTANCE 等）
            String alias = POTION_ALIASES.get(key);
            if (alias != null) {
                found = potion(alias);
            }
        }

        if (found == null) {
            warn("未知药水效果: " + raw + "（该动作将被忽略）");
        }
        POTION_CACHE.put(key, found);
        return found;
    }

    // ------------------------------------------------------------------ enchantment

    public static Enchantment enchantment(NamespacedKey key) {
        if (key == null) {
            return null;
        }
        String cacheKey = key.toString();
        if (ENCHANT_CACHE.containsKey(cacheKey)) {
            return ENCHANT_CACHE.get(cacheKey);
        }
        Enchantment found = null;
        try {
            Registry<Enchantment> reg = registry(Enchantment.class);
            if (reg != null) {
                found = reg.get(key);
            }
        } catch (Throwable ignored) {
        }
        if (found == null) {
            try {
                found = Enchantment.getByKey(key);
            } catch (Throwable ignored) {
            }
        }
        if (found == null) {
            try {
                found = Enchantment.getByName(key.getKey().toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) {
            }
        }
        ENCHANT_CACHE.put(cacheKey, found);
        return found;
    }

    // ------------------------------------------------------------------ enum 安全解析

    /**
     * 枚举安全解析：版本间枚举常量增删（如 Particle.REDSTONE -> DUST）不会导致异常，
     * 只返回 null 并给出一次告警。
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String cacheKey = type.getName() + "#" + name;
        if (ENUM_CACHE.containsKey(cacheKey)) {
            return (E) ENUM_CACHE.get(cacheKey);
        }
        E result = null;
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            result = Enum.valueOf(type, normalized);
        } catch (Throwable ignored) {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(normalized)
                        || constant.name().replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                    result = constant;
                    break;
                }
            }
        }
        // 跨版本重命名（主要是 Particle）：用对端名字再试一次
        if (result == null) {
            String alias = ParticleAliases.alias(normalized);
            if (alias != null) {
                try {
                    result = Enum.valueOf(type, alias);
                } catch (Throwable ignored) {
                    for (E constant : type.getEnumConstants()) {
                        if (constant.name().equalsIgnoreCase(alias)) {
                            result = constant;
                            break;
                        }
                    }
                }
            }
        }
        if (result == null) {
            warn("当前服务端版本不支持 " + type.getSimpleName() + ": " + name + "（已忽略该效果）");
        }
        ENUM_CACHE.put(cacheKey, result);
        return result;
    }

    // ------------------------------------------------------------------ 自定义附魔注册

    /**
     * 把 YAML 定义的附魔注册进服务端 Registry。
     * 1.21.x：Registry#register（Bukkit.getRegistry(Enchantment.class)）。
     * 老版本：Registry 不可写 -> 返回 false，插件自动降级为「纯 PDC/Lore 模式」。
     *
     * @return 成功注册（或已存在）返回 true
     */
    @SuppressWarnings("unchecked")
    public static boolean registerEnchantment(NamespacedKey key, String displayName, int maxLevel) {
        if (enchantment(key) != null) {
            return true;
        }
        try {
            Registry<Enchantment> reg = registry(Enchantment.class);
            if (reg == null) {
                return false;
            }
            Method register = reg.getClass().getMethod("register", NamespacedKey.class, Object.class);
            Object value = buildEnchantment(key, displayName, maxLevel);
            if (value == null) {
                return false;
            }
            register.invoke(reg, key, value);
            ENCHANT_CACHE.remove(key.toString());
            return enchantment(key) != null;
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "注册自定义附魔失败: " + key, t);
            }
            return false;
        }
    }

    /**
     * 构造 Enchantment 实例：
     * 1.21 起构造器为 (NamespacedKey, String)；旧版为 (NamespacedKey) 或需要 holder。
     * 依次尝试，全部失败则由调用方降级。
     */
    private static Object buildEnchantment(NamespacedKey key, String displayName, int maxLevel) {
        try {
            Constructor<Enchantment> c = Enchantment.class.getDeclaredConstructor(NamespacedKey.class, String.class);
            c.setAccessible(true);
            return c.newInstance(key, displayName);
        } catch (Throwable ignored) {
        }
        try {
            Constructor<Enchantment> c = Enchantment.class.getDeclaredConstructor(NamespacedKey.class);
            c.setAccessible(true);
            return c.newInstance(key);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
