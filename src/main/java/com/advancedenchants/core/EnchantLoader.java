package com.advancedenchants.core;

import com.advancedenchants.model.EnchantDef;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * 附魔配置加载器。
 * 读取 plugins/AdvancedEnchants/enchants/*.yml（首次运行从 jar 内释放默认配置），
 * 解析为 EnchantDef 并注册；任何单条错误只告警不中断。
 */
public final class EnchantLoader {

    private final JavaPlugin plugin;

    /** 等级默认权重（来自 config.yml 的 tier-weights，未配置时用 Tier 内置值）。 */
    private final Map<com.advancedenchants.model.Tier, Integer> tierWeights =
            new EnumMap<>(com.advancedenchants.model.Tier.class);

    public EnchantLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        for (com.advancedenchants.model.Tier tier : com.advancedenchants.model.Tier.values()) {
            tierWeights.put(tier, plugin.getConfig().getInt("tier-weights." + tier.name(), tier.defaultWeight()));
        }
    }

    /** 某等级的默认随机权重（config.yml 可覆盖）。 */
    public int tierWeight(com.advancedenchants.model.Tier tier) {
        return tierWeights.getOrDefault(tier, tier.defaultWeight());
    }

    /** 加载结果统计。 */
    public record Result(int loaded, int failedFiles, int invalidEntries, List<String> warnings) {
    }

    public Result loadAll(EnchantRegistry registry, File folder) {
        List<String> warnings = new ArrayList<>();
        int failedFiles = 0;
        int invalid = 0;

        extractDefaults(folder);

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            warnings.add("enchants 目录为空：" + folder.getAbsolutePath());
            return new Result(0, 0, 0, warnings);
        }
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = yaml.getConfigurationSection("enchants");
                if (root == null) {
                    warnings.add(file.getName() + " 缺少顶层 enchants: 节点");
                    continue;
                }
                for (String id : root.getKeys(false)) {
                    try {
                        ConfigurationSection sec = root.getConfigurationSection(id);
                        if (sec == null) {
                            warnings.add(file.getName() + " -> " + id + " 不是有效节点");
                            invalid++;
                            continue;
                        }
                        EnchantDef def = parseEntry(id, sec, warnings);
                        if (def == null) {
                            invalid++;
                            continue;
                        }
                        boolean existed = registry.get(id) != null;
                        registry.register(def);
                        if (existed) {
                            warnings.add("附魔 ID 重复，后加载的覆盖前者: " + id);
                        }
                    } catch (Exception e) {
                        invalid++;
                        warnings.add(file.getName() + " -> " + id + " 解析异常: " + e.getMessage());
                        plugin.getLogger().log(Level.FINE, "解析附魔失败: " + id, e);
                    }
                }
            } catch (Exception e) {
                failedFiles++;
                plugin.getLogger().log(Level.WARNING, "读取附魔文件失败: " + file.getName(), e);
            }
        }
        return new Result(registry.size(), failedFiles, invalid, warnings);
    }

    /** 首次运行把 jar 内的默认附魔配置释放到数据目录（已存在则不覆盖）。 */
    public void extractDefaults(File folder) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建目录: " + folder.getAbsolutePath());
            return;
        }
        String[] defaults = {
                "10-common.yml", "20-uncommon.yml", "30-rare.yml",
                "40-epic.yml", "50-legendary.yml", "60-mythic.yml",
                "custom.yml"
        };
        for (String name : defaults) {
            File target = new File(folder, name);
            if (target.exists()) {
                continue;
            }
            String resource = "enchants/" + name;
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    continue;
                }
                java.nio.file.Files.copy(in, target.toPath());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "释放默认配置失败: " + name, e);
            }
        }
    }

    /** 从 jar 内 resource 直接读默认配置（用于 /ae list 与自检）。 */
    public YamlConfiguration readResource(String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private EnchantDef parseEntry(String id, ConfigurationSection sec, List<String> warnings) {
        String cleanId = id.trim().toLowerCase(Locale.ROOT);
        if (!cleanId.matches("^[a-z0-9_]{2,40}$")) {
            warnings.add("非法附魔 ID（需 ^[a-z0-9_]{2,40}$）: " + id);
            return null;
        }
        if (!sec.getBoolean("enabled", true)) {
            return null;
        }
        String display = sec.getString("display", cleanId);
        String tierRaw = sec.getString("tier", "COMMON");
        com.advancedenchants.model.Tier tier = com.advancedenchants.model.Tier.parse(tierRaw);
        if (!tier.name().equalsIgnoreCase(tierRaw.trim())) {
            warnings.add(cleanId + " 未知等级 '" + tierRaw + "'，按 COMMON 处理");
        }
        int maxLevel = Math.max(1, Math.min(10, sec.getInt("max-level", 1)));

        java.util.Set<com.advancedenchants.model.ApplyType> types =
                java.util.EnumSet.noneOf(com.advancedenchants.model.ApplyType.class);
        for (String raw : sec.getStringList("types")) {
            com.advancedenchants.model.ApplyType t = com.advancedenchants.model.ApplyType.parse(raw);
            if (t == null) {
                warnings.add(cleanId + " 未知类型: " + raw);
            } else {
                types.add(t);
            }
        }
        if (types.isEmpty()) {
            warnings.add(cleanId + " 未配置任何有效 types，条目已跳过");
            return null;
        }

        List<String> description = sec.getStringList("description");
        List<String> conflicts = new ArrayList<>();
        for (String c : sec.getStringList("conflicts")) {
            conflicts.add(c.trim().toLowerCase(Locale.ROOT));
        }
        int weight = sec.getInt("rarity-weight", tierWeight(tier));

        Map<com.advancedenchants.model.Trigger, List<com.advancedenchants.model.TriggerDef>> triggers =
                new EnumMap<>(com.advancedenchants.model.Trigger.class);
        ConfigurationSection triggerRoot = sec.getConfigurationSection("triggers");
        if (triggerRoot == null) {
            warnings.add(cleanId + " 缺少 triggers 节点，条目已跳过");
            return null;
        }
        for (String triggerName : triggerRoot.getKeys(false)) {
            com.advancedenchants.model.Trigger trigger = com.advancedenchants.model.Trigger.parse(triggerName);
            if (trigger == null) {
                warnings.add(cleanId + " 未知触发器: " + triggerName + "（已忽略）");
                continue;
            }
            // 支持两种写法：直接字段，或列表形式（同一触发器多套参数）
            List<ConfigurationSection> blocks = new ArrayList<>();
            if (triggerRoot.isList(triggerName)) {
                // 列表式：triggers.ON_HIT 下是 - actions: [...] 的数组（推荐，可写多套参数）
                for (Object o : triggerRoot.getList(triggerName, List.of())) {
                    if (o instanceof Map<?, ?> map) {
                        blocks.add(sectionOf(map));
                    }
                }
            } else {
                ConfigurationSection direct = triggerRoot.getConfigurationSection(triggerName);
                if (direct != null && direct.contains("actions")) {
                    blocks.add(direct);
                } else if (direct != null && direct.isList("actions")) {
                    YamlConfiguration tmp = new YamlConfiguration();
                    for (String key : direct.getKeys(false)) {
                        tmp.set(key, direct.get(key));
                    }
                    blocks.add(tmp);
                } else {
                    warnings.add(cleanId + " 触发器 " + triggerName + " 未定义 actions（已忽略）");
                    continue;
                }
            }
            if (blocks.isEmpty()) {
                warnings.add(cleanId + " 触发器 " + triggerName + " 内容为空（已忽略）");
                continue;
            }

            List<com.advancedenchants.model.TriggerDef> defs =
                    triggers.computeIfAbsent(trigger, k -> new ArrayList<>());
            for (ConfigurationSection block : blocks) {
                com.advancedenchants.model.TriggerDef def = parseTrigger(cleanId, trigger, block, warnings);
                if (def != null) {
                    defs.add(def);
                }
            }
            if (defs.isEmpty()) {
                triggers.remove(trigger);
            }
        }
        if (triggers.isEmpty()) {
            warnings.add(cleanId + " 没有任何有效触发器，条目已跳过");
            return null;
        }

        EnchantDef def = new EnchantDef(cleanId, display, tier, maxLevel, types, description,
                conflicts, weight, triggers);
        validateActions(def, warnings);
        return def;
    }

    private com.advancedenchants.model.TriggerDef parseTrigger(String id,
                                                              com.advancedenchants.model.Trigger trigger,
                                                              ConfigurationSection block,
                                                              List<String> warnings) {
        double cooldown = block.getDouble("cooldown", 0.0);
        double chance = block.getDouble("chance", 100.0);
        double chancePerLevel = block.getDouble("chance-per-level", 0.0);

        List<com.advancedenchants.model.CondDef> conditions = new ArrayList<>();
        for (Map<?, ?> raw : block.getMapList("conditions")) {
            String type = raw.get("type") == null ? null : String.valueOf(raw.get("type"));
            if (type == null) {
                warnings.add(id + " 条件缺少 type（已忽略）");
                continue;
            }
            conditions.add(new com.advancedenchants.model.CondDef(type.trim().toUpperCase(Locale.ROOT), castMap(raw)));
        }

        List<com.advancedenchants.model.ActionDef> actions = new ArrayList<>();
        for (Map<?, ?> raw : block.getMapList("actions")) {
            String type = raw.get("type") == null ? null : String.valueOf(raw.get("type"));
            if (type == null) {
                warnings.add(id + " 动作缺少 type（已忽略）");
                continue;
            }
            actions.add(new com.advancedenchants.model.ActionDef(type.trim().toUpperCase(Locale.ROOT), castMap(raw)));
        }
        if (actions.isEmpty()) {
            warnings.add(id + " 触发器 " + trigger + " 无有效动作（已忽略）");
            return null;
        }
        return new com.advancedenchants.model.TriggerDef(trigger, (long) (cooldown * 1000.0),
                chance, chancePerLevel, conditions, actions);
    }

    private Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    /** 把列表元素（Map）转换为 ConfigurationSection，便于统一读取。 */
    private ConfigurationSection sectionOf(Map<?, ?> map) {
        YamlConfiguration tmp = new YamlConfiguration();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            tmp.set(String.valueOf(e.getKey()), e.getValue());
        }
        return tmp;
    }

    /** 校验动作/条件类型是否已实现（未实现只告警）。 */
    private void validateActions(EnchantDef def, List<String> warnings) {
        for (List<com.advancedenchants.model.TriggerDef> list : def.triggers().values()) {
            for (com.advancedenchants.model.TriggerDef td : list) {
                for (com.advancedenchants.model.ActionDef a : td.actions()) {
                    if (!Actions.KNOWN.contains(a.type())) {
                        warnings.add("附魔 " + def.id() + " 使用了未知动作: " + a.type());
                    }
                }
                for (com.advancedenchants.model.CondDef c : td.conditions()) {
                    if (!Conditions.KNOWN.contains(c.type())) {
                        warnings.add("附魔 " + def.id() + " 使用了未知条件: " + c.type());
                    }
                }
            }
        }
    }

    /** 已实现的动作类型白名单。 */
    public static final class Actions {
        public static final java.util.Set<String> KNOWN = java.util.Set.of(
                "DAMAGE_MULTIPLY", "DAMAGE_ADD", "TRUE_DAMAGE", "PERCENT_CURRENT_HP", "PERCENT_MAX_HP",
                "EXECUTE", "LIFESTEAL", "HEAL", "DAMAGE_REDUCE", "DODGE", "DAMAGE_CAP", "REFLECT", "MARK",
                "ARMOR_DAMAGE", "ARMOR_PIERCE", "CONVERT_DROP", "MULTIPLY_DROP",
                "POTION", "POTION_SELF", "CLEAR_POTION", "IGNITE", "EXTINGUISH", "KNOCKBACK", "LAUNCH",
                "PULL", "TELEPORT", "SUMMON", "LIGHTNING", "EXPLOSION", "POTION_CLOUD", "SPAWN_PROJECTILE",
                "ARROW_RAIN", "SHOCKWAVE", "FREEZE", "SET_BLOCK", "BREAK_BLOCK", "VEIN_MINE", "TREE_FELL",
                "SHEAR_AREA", "AUTO_REPLANT", "AUTO_SMELT", "DURABILITY", "SOULS", "SOULBOUND", "CANCEL_EVENT",
                "SURVIVE", "ATTACK_SPEED", "CONSUME_HELD", "COMMAND", "MESSAGE", "TITLE", "ACTIONBAR",
                "PARTICLE", "SOUND", "ADD_XP", "MULTIPLY_XP", "XP_MULTIPLY", "RUN_SCRIPT");

        private Actions() {
        }
    }

    /** 已实现的条件类型白名单。 */
    public static final class Conditions {
        public static final java.util.Set<String> KNOWN = java.util.Set.of(
                "CHANCE", "HEALTH", "TARGET_HEALTH", "SNEAK", "SPRINT", "ON_GROUND", "IN_WATER", "IN_RAIN",
                "BURNING", "TARGET_BURNING", "TIME", "Y", "WORLD", "BIOME", "BLOCK", "TARGET_TYPE",
                "HAS_POTION", "PERMISSION", "SOULS", "HAS_ENCHANT", "DAY", "NIGHT", "DIFFICULTY",
                "PROJECTILE_DISTANCE", "FORCE", "NEARBY_ALLIES");

        private Conditions() {
        }
    }

    /** 便捷：从 key 得到 Bukkit 附魔（含未注册时的降级）。 */
    public static Enchantment resolveEnchantment(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        NamespacedKey key = id.contains(":")
                ? NamespacedKey.fromString(id.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT));
        return key == null ? null : com.advancedenchants.util.VersionSafe.enchantment(key);
    }
}
