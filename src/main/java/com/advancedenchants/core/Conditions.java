package com.advancedenchants.core;

import com.advancedenchants.model.CondDef;
import com.advancedenchants.model.Levels;
import com.advancedenchants.util.VersionSafe;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 条件求值（SCHEMA.md 第 4 节）。
 * 全部条件必须同时满足；任何异常都视为「不通过」，不会打断事件。
 */
public final class Conditions {

    private Conditions() {
    }

    /** 等级数值公式，转发到 model.Levels。 */
    public static double scaled(Map<String, Object> data, int level, int maxLevel) {
        return Levels.scaled(data, level, maxLevel);
    }

    public static boolean test(TriggerCtx ctx, CondDef cond, int level) {
        try {
            return switch (cond.type()) {
                case "CHANCE" -> chance(cond, level);
                case "HEALTH" -> compareRatio(ratio(ctx.player.getHealth(), maxHealth(ctx.player)),
                        valueOr(cond, level), !"ABOVE".equalsIgnoreCase(cond.s("mode", "BELOW")));
                case "TARGET_HEALTH" -> ctx.victim != null && compareRatio(
                        ratio(ctx.victim.getHealth(), maxHealth(ctx.victim)),
                        valueOr(cond, level), !"ABOVE".equalsIgnoreCase(cond.s("mode", "BELOW")));
                case "SNEAK" -> ctx.player.isSneaking() == cond.bool("value", true);
                case "SPRINT" -> ctx.player.isSprinting() == cond.bool("value", true);
                case "ON_GROUND" -> ctx.player.isOnGround() == cond.bool("value", true);
                case "IN_WATER" -> ctx.player.isInWater() == cond.bool("value", true);
                case "IN_RAIN" -> isRaining(ctx.player.getWorld()) == cond.bool("value", true);
                case "BURNING" -> (ctx.player.getFireTicks() > 0) == cond.bool("value", true);
                case "TARGET_BURNING" -> ctx.victim != null && (ctx.victim.getFireTicks() > 0) == cond.bool("value", true);
                case "DAY" -> isDay(ctx.player.getWorld());
                case "NIGHT" -> !isDay(ctx.player.getWorld());
                case "TIME" -> inRange(ctx.player.getWorld().getTime(), cond.d("min", 0), cond.d("max", 24000));
                case "Y" -> inRange(ctx.player.getLocation().getY(), cond.d("min", -128), cond.d("max", 512));
                case "WORLD" -> matchesWorld(ctx.player.getWorld(), cond.list("value"));
                case "BIOME" -> matchesBiome(ctx.player.getLocation().getBlock().getBiome(), cond.list("value"));
                case "BLOCK" -> matchesBlock(ctx.block, cond.list("value"), cond.bool("blacklist", false));
                case "TARGET_TYPE" -> matchesEntityType(ctx.victim != null ? ctx.victim : ctx.target,
                        cond.list("value"), cond.bool("blacklist", false));
                case "HAS_POTION" -> hasPotion(ctx.player, cond.list("value"), cond.i("min-level", 1));
                case "PERMISSION" -> hasPermission(ctx.player, cond.s("value", ""));
                case "SOULS" -> ctx.items.stream().anyMatch(i ->
                        com.advancedenchants.storage.ItemTags.souls(i) >= valueOr(cond, level));
                case "HAS_ENCHANT" -> hasEnchant(cond, level);
                case "DIFFICULTY" -> matchesDifficulty(ctx.player.getWorld(), cond.list("value"));
                case "PROJECTILE_DISTANCE" -> inRange(ctx.distance, cond.d("min", 0), cond.d("max", 512));
                case "FORCE" -> ctx.force >= valueOr(cond, level);
                case "NEARBY_ALLIES" -> {
                    int allies = 0;
                    double radius = cond.d("radius", 10);
                    for (Player other : ctx.player.getWorld().getPlayers()) {
                        if (other.equals(ctx.player) || !other.isOnline()) {
                            continue;
                        }
                        if (other.getLocation().distanceSquared(ctx.player.getLocation()) <= radius * radius) {
                            allies++;
                        }
                    }
                    yield inRange(allies, cond.d("min", 1), cond.d("max", 100));
                }
                default -> true;
            };
        } catch (Throwable t) {
            return false;
        }
    }

    // -------------------------------------------------------------- 辅助

    private static boolean chance(CondDef cond, int level) {
        double p = valueOr(cond, level);
        if (p <= 0) {
            return false;
        }
        return p >= 100 || Math.random() * 100.0 < p;
    }

    private static double valueOr(CondDef cond, int level) {
        return Levels.scaled(cond.data(), level, 10);
    }

    private static double maxHealth(LivingEntity entity) {
        return Math.max(1.0, entity.getMaxHealth());
    }

    private static double ratio(double value, double max) {
        return max <= 0 ? 0 : value / max;
    }

    private static boolean compareRatio(double actual, double expected, boolean lessThan) {
        // 用 epsilon 容错：比例上限恰好为 1.0，而配置常写 "满血 = 1.0"（不能用严格 > 比较）
        double eps = 1.0E-6;
        return lessThan ? actual < expected - eps : actual > expected - eps;
    }

    private static boolean inRange(double value, double min, double max) {
        return value >= Math.min(min, max) && value <= Math.max(min, max);
    }

    private static boolean isDay(World world) {
        long t = world.getTime();
        return t < 12300 || t > 23850;
    }

    private static boolean isRaining(World world) {
        return world.hasStorm() || world.isThundering();
    }

    private static boolean matchesWorld(World world, List<String> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        for (String p : patterns) {
            if (wildcard(p, world.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBiome(Biome biome, List<String> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        String key = biomeKey(biome);
        for (String p : patterns) {
            if (wildcard(p, key) || wildcard(p, biome.name())) {
                return true;
            }
        }
        return false;
    }

    private static String biomeKey(Biome biome) {
        try {
            return biome.getKey().getKey();
        } catch (Throwable t) {
            return biome.name().toLowerCase(Locale.ROOT);
        }
    }

    private static boolean matchesBlock(Block block, List<String> patterns, boolean blacklist) {
        if (patterns.isEmpty()) {
            return true;
        }
        if (block == null) {
            return blacklist;
        }
        Material material = block.getType();
        String key = materialKey(material);
        boolean matched = false;
        for (String p : patterns) {
            if (wildcard(p, key) || wildcard(p, material.name())) {
                matched = true;
                break;
            }
        }
        return blacklist != matched;
    }

    private static String materialKey(Material material) {
        try {
            return material.getKey().getKey();
        } catch (Throwable t) {
            return material.name().toLowerCase(Locale.ROOT);
        }
    }

    private static boolean matchesEntityType(Entity entity, List<String> patterns, boolean blacklist) {
        if (patterns.isEmpty()) {
            return true;
        }
        if (entity == null) {
            return blacklist;
        }
        EntityType type = entity.getType();
        String key = type.getKey().getKey();
        boolean matched = false;
        for (String p : patterns) {
            if (wildcard(p, key) || wildcard(p, type.name())) {
                matched = true;
                break;
            }
        }
        return blacklist != matched;
    }

    private static boolean hasPotion(Player player, List<String> names, int minLevel) {
        if (names.isEmpty()) {
            return true;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            String effectKey = effect.getType().getKey().getKey();
            for (String raw : names) {
                if (raw.equalsIgnoreCase(effectKey) || raw.equalsIgnoreCase(effect.getType().getName())
                        || wildcard(raw, effectKey)) {
                    if (effect.getAmplifier() + 1 >= minLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasPermission(Player player, String node) {
        if (node == null || node.isBlank()) {
            return true;
        }
        if (node.startsWith("!")) {
            return !player.hasPermission(node.substring(1));
        }
        return player.hasPermission(node);
    }

    private static boolean hasEnchant(CondDef cond, int level) {
        String raw = cond.s("value", "");
        int minLevel = cond.i("min-level", 1);
        Enchantment enchantment = resolveEnchantment(raw);
        if (enchantment == null) {
            return false;
        }
        for (org.bukkit.inventory.ItemStack item : pendingItems) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchant(enchantment) && meta.getEnchantLevel(enchantment) >= minLevel) {
                return true;
            }
        }
        return false;
    }

    /** HAS_ENCHANT 需要访问当前判定的物品，由 Engine 在执行前置入。 */
    private static final ThreadLocal<List<org.bukkit.inventory.ItemStack>> ITEMS = new ThreadLocal<>();

    // 兼容：单测/旧调用点使用的静态占位
    private static List<org.bukkit.inventory.ItemStack> pendingItems = java.util.List.of();

    public static void bindItems(List<org.bukkit.inventory.ItemStack> items) {
        pendingItems = items == null ? java.util.List.of() : items;
    }

    private static Enchantment resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.contains(":")) {
            return VersionSafe.enchantment(org.bukkit.NamespacedKey.fromString(key));
        }
        Enchantment minecraft = VersionSafe.enchantment(org.bukkit.NamespacedKey.minecraft(key));
        if (minecraft != null) {
            return minecraft;
        }
        return VersionSafe.enchantment(org.bukkit.NamespacedKey.fromString("advancedenchants:" + key));
    }

    private static boolean matchesDifficulty(World world, List<String> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        String name = world.getDifficulty().name();
        for (String p : patterns) {
            if (p.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 带缓存的通配匹配：* 匹配任意长度，大小写不敏感，允许省略命名空间。
     *
     * 热路径优化：ON_BLOCK_BREAK / ON_LOOT 每次触发都会对每个 BLOCK 条件做匹配，
     * 逐个 Pattern.compile 会造成可观测的卡顿，所以缓存「模式 -> 正则」。
     * 缓存只由配置里的模式字符串填充，天然有界。
     */
    private static final Map<String, java.util.regex.Pattern> WILDCARD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean wildcard(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        String v = value.toLowerCase(Locale.ROOT);
        if (p.equals(v)) {
            return true;
        }
        if (p.contains(":")) {
            String shortP = p.substring(p.indexOf(':') + 1);
            if (!v.contains(":") && wildcard(shortP, v)) {
                return true;
            }
        }
        if (!p.contains("*")) {
            return false;
        }
        java.util.regex.Pattern compiled = WILDCARD_CACHE.computeIfAbsent(p,
                key -> java.util.regex.Pattern.compile(key.replace(".", "\\.").replace("*", ".*")));
        return compiled.matcher(v).matches();
    }

    /** 缓存规模（诊断用）。 */
    public static int wildcardCacheSize() {
        return WILDCARD_CACHE.size();
    }

    /** 药水解析转发（供动作使用）。 */
    public static PotionEffectType potion(String raw) {
        return VersionSafe.potion(raw);
    }

    /** 供调试/文档输出：条件类型全集。 */
    public static java.util.Set<String> known() {
        return com.advancedenchants.core.EnchantLoader.Conditions.KNOWN;
    }
}
