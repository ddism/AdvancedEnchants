package com.advancedenchants.verify;

import com.advancedenchants.core.EnchantLoader;
import com.advancedenchants.model.Trigger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 独立的附魔配置校验器（不需要启动服务端，也不需要 Paper 的 YamlConfiguration）。
 *
 * 用法：
 *   java -cp "target/classes;&lt;snakeyaml.jar&gt;" com.advancedenchants.verify.YamlVerifier src/main/resources/enchants
 *
 * 说明：校验器通过反射调用 SnakeYAML，因此主插件编译期不依赖 SnakeYAML；
 * 运行时把 snakeyaml.jar 放进 -cp 即可（.verify-libs/snakeyaml-2.2.jar）。
 *
 * 校验内容：
 *  1) YAML 可解析、顶层为 enchants、无重复 ID（跨文件）
 *  2) 每条必需字段：display / tier / max-level / types / triggers（≥1，每块 ≥1 actions）
 *  3) tier / types / 触发器 / 条件 / 动作 是否在契约白名单内
 *  4) 数值自洽：max >= base；比例类字段不应写成百分数
 *  5) 安全规则：EXPLOSION 不得 set-fire/break-blocks；BREAK_BLOCK/VEIN_MINE 必须有 limit；
 *     SUMMON 必须有 lifetime；COMMAND 需显式开启
 */
public final class YamlVerifier {

    private static int errors;
    private static int warnings;
    private static final Set<String> TIERS = Set.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    private static final Set<String> TYPES = Set.of(
            "SWORD", "AXE", "TRIDENT", "BOW", "CROSSBOW", "ARMOR", "SHIELD", "PICKAXE",
            "SHOVEL", "HOE", "SHEARS", "FISHING_ROD", "ELYTRA", "ALL");
    private static final Set<String> RESERVED_KEYS = Set.of(
            "display", "tier", "max-level", "types", "description", "conflicts",
            "rarity-weight", "enabled", "triggers");

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "src/main/resources/enchants");
        if (!dir.isDirectory()) {
            System.out.println("目录不存在: " + dir.getAbsolutePath());
            System.exit(2);
        }
        Map<String, String> owner = new LinkedHashMap<>();
        Map<String, Integer> tierCount = new TreeMap<>();
        Map<String, Set<String>> typeCount = new TreeMap<>();
        Map<String, Integer> triggerUse = new TreeMap<>();
        Map<String, Integer> actionUse = new TreeMap<>();
        Map<String, Integer> conditionUse = new TreeMap<>();
        int total = 0;
        int blocks = 0;
        int actionsTotal = 0;

        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            System.out.println("目录内没有 yml 文件");
            System.exit(2);
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        YamlFacade yaml = new YamlFacade();
        for (File file : files) {
            Map<String, Object> root;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Object loaded = yaml.load(reader);
                if (!(loaded instanceof Map<?, ?> map)) {
                    error(file.getName(), "顶层不是映射");
                    continue;
                }
                root = castMap(map);
            } catch (Throwable t) {
                error(file.getName(), "YAML 解析失败: " + t);
                continue;
            }
            Object enchantsNode = root.get("enchants");
            if (!(enchantsNode instanceof Map<?, ?> enchants)) {
                error(file.getName(), "缺少顶层 enchants: 节点");
                continue;
            }
            for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                String id = String.valueOf(entry.getKey());
                total++;
                if (owner.containsKey(id)) {
                    error(file.getName(), "ID 重复: " + id + "（已在 " + owner.get(id) + "）");
                }
                owner.put(id, file.getName());
                if (!(entry.getValue() instanceof Map<?, ?> rawEntry)) {
                    error(file.getName(), id + " 不是配置节");
                    continue;
                }
                Map<String, Object> sec = castMap(rawEntry);
                int used = checkEntry(file.getName(), id, sec, tierCount, typeCount,
                        triggerUse, actionUse, conditionUse);
                blocks += used;
            }
        }

        for (File file : files) {
            // 统计 action 总数（上面 checkEntry 里已累计，此处仅打印）
        }

        System.out.println("──────── 校验汇总 ────────");
        System.out.println("文件数: " + files.length + "  附魔总数: " + total + "  唯一 ID: " + owner.size());
        System.out.println("等级分布: " + tierCount);
        StringBuilder types = new StringBuilder();
        typeCount.forEach((t, ids) -> types.append(t).append('=').append(ids.size()).append(' '));
        System.out.println("类型覆盖: " + types.toString().trim());
        System.out.println("触发器使用: " + triggerUse);
        System.out.println("动作种类: " + actionUse.size() + " 种 " + actionUse);
        System.out.println("条件种类: " + conditionUse.size() + " 种 " + conditionUse);
        System.out.println("触发器块: " + blocks);
        System.out.println("错误: " + errors + "  警告: " + warnings);
        System.out.println(errors == 0 ? "结果: PASS" : "结果: FAIL");
        System.exit(errors == 0 ? 0 : 1);
    }

    private static int checkEntry(String file, String id, Map<String, Object> sec,
                                  Map<String, Integer> tierCount, Map<String, Set<String>> typeCount,
                                  Map<String, Integer> triggerUse, Map<String, Integer> actionUse,
                                  Map<String, Integer> conditionUse) {
        Object display = sec.get("display");
        if (display == null || String.valueOf(display).isBlank()) {
            error(file, id + " 缺少 display");
        }
        String tierRaw = sec.get("tier") == null ? "" : String.valueOf(sec.get("tier")).toUpperCase(Locale.ROOT);
        if (!TIERS.contains(tierRaw)) {
            error(file, id + " 非法 tier: " + sec.get("tier"));
        } else {
            tierCount.merge(tierRaw, 1, Integer::sum);
        }
        double maxLevel = num(sec.get("max-level"), 0);
        if (maxLevel < 1 || maxLevel > 10) {
            error(file, id + " max-level 应为 1~10，实际 " + sec.get("max-level"));
        }
        List<String> types = strList(sec.get("types"));
        if (types.isEmpty()) {
            error(file, id + " 缺少 types");
        }
        for (String type : types) {
            String t = type.toUpperCase(Locale.ROOT);
            if (!TYPES.contains(t)) {
                error(file, id + " 非法 type: " + type);
            } else {
                typeCount.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(id);
            }
        }
        Object enabled = sec.get("enabled");
        if (enabled != null && !bool(enabled)) {
            warning(file, id + " enabled=false（不会被注册）");
        }

        Object triggersNode = sec.get("triggers");
        if (!(triggersNode instanceof Map<?, ?> triggers)) {
            error(file, id + " 缺少 triggers");
            return 0;
        }
        int used = 0;
        for (Map.Entry<?, ?> tEntry : triggers.entrySet()) {
            String triggerName = String.valueOf(tEntry.getKey());
            if (Trigger.parse(triggerName) == null) {
                error(file, id + " 未知触发器: " + triggerName);
                continue;
            }
            triggerUse.merge(triggerName.toUpperCase(Locale.ROOT), 1, Integer::sum);
            List<Map<String, Object>> blocks = new ArrayList<>();
            if (tEntry.getValue() instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        blocks.add(castMap(m));
                    }
                }
            } else if (tEntry.getValue() instanceof Map<?, ?> m) {
                blocks.add(castMap(m));
            }
            if (blocks.isEmpty()) {
                error(file, id + " / " + triggerName + " 内容为空");
                continue;
            }
            for (Map<String, Object> block : blocks) {
                used++;
                checkConditions(file, id, triggerName, block, conditionUse);
                Object actionsNode = block.get("actions");
                if (!(actionsNode instanceof List<?> actionList) || actionList.isEmpty()) {
                    error(file, id + " / " + triggerName + " 没有 actions");
                    continue;
                }
                for (Object actionObj : actionList) {
                    if (!(actionObj instanceof Map<?, ?> actionMap)) {
                        error(file, id + " / " + triggerName + " action 不是映射");
                        continue;
                    }
                    checkAction(file, id, triggerName, castMap(actionMap), actionUse, conditionUse);
                }
            }
        }
        if (used == 0) {
            error(file, id + " 没有任何有效触发器块");
        }
        return used;
    }

    private static void checkConditions(String file, String id, String trigger, Map<String, Object> block,
                                        Map<String, Integer> conditionUse) {
        Object conditionsNode = block.get("conditions");
        if (conditionsNode == null) {
            return;
        }
        if (!(conditionsNode instanceof List<?> list)) {
            error(file, id + " / " + trigger + " conditions 不是列表");
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> cond)) {
                error(file, id + " / " + trigger + " condition 不是映射");
                continue;
            }
            Object type = cond.get("type");
            if (type == null) {
                error(file, id + " / " + trigger + " 条件缺少 type");
                continue;
            }
            String t = String.valueOf(type).toUpperCase(Locale.ROOT);
            if (!EnchantLoader.Conditions.KNOWN.contains(t)) {
                error(file, id + " / " + trigger + " 未知条件: " + t);
            } else {
                conditionUse.merge(t, 1, Integer::sum);
            }
        }
    }

    private static void checkAction(String file, String id, String trigger, Map<String, Object> action,
                                    Map<String, Integer> actionUse, Map<String, Integer> conditionUse) {
        Object typeObj = action.get("type");
        if (typeObj == null) {
            error(file, id + " / " + trigger + " 动作缺少 type");
            return;
        }
        String type = String.valueOf(typeObj).toUpperCase(Locale.ROOT);
        actionUse.merge(type, 1, Integer::sum);
        if (!EnchantLoader.Actions.KNOWN.contains(type)) {
            error(file, id + " / " + trigger + " 未知动作: " + type);
            return;
        }
        double base = num(action.get("base"), 0);
        double perLevel = num(action.get("per-level"), 0);
        if (action.containsKey("max")) {
            double max = num(action.get("max"), Double.MAX_VALUE);
            if (max < base) {
                warning(file, id + " / " + type + " max(" + max + ") < base(" + base + ")");
            }
        }
        if (perLevel < 0 && base <= 0 && !type.equals("DURABILITY")) {
            warning(file, id + " / " + type + " per-level 为负且 base<=0");
        }
        switch (type) {
            case "DAMAGE_MULTIPLY", "DAMAGE_REDUCE", "DODGE", "REFLECT", "LIFESTEAL", "ARMOR_PIERCE",
                 "PERCENT_CURRENT_HP", "PERCENT_MAX_HP", "CONVERT_DROP", "MARK" -> {
                if (base > 3.0) {
                    warning(file, id + " / " + type + " base=" + base + " 疑似把比例写成了百分数");
                }
            }
            case "EXPLOSION" -> {
                if (bool(action.get("set-fire")) || bool(action.get("break-blocks"))) {
                    warning(file, id + " EXPLOSION 允许地形破坏（set-fire/break-blocks）");
                }
            }
            case "BREAK_BLOCK", "VEIN_MINE" -> {
                if (!action.containsKey("limit")) {
                    warning(file, id + " / " + type + " 未设置 limit，可能破坏大量方块");
                }
            }
            case "SUMMON" -> {
                if (!action.containsKey("lifetime")) {
                    warning(file, id + " SUMMON 未设置 lifetime，实体不会自动清理");
                }
            }
            case "COMMAND" -> warning(file, id + " 使用了 COMMAND 动作（需 config.yml 开启 allow-commands）");
            default -> {
                // 其它动作无额外规则
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * SnakeYAML 的最小反射封装：避免主工程编译期依赖 org.yaml:snakeyaml。
     * 若 classpath 中缺少 snakeyaml，会给出清晰的提示而不是 NoClassDefFoundError。
     */
    private static final class YamlFacade {
        private final Object instance;
        private final java.lang.reflect.Method load;

        YamlFacade() {
            try {
                Class<?> clazz = Class.forName("org.yaml.snakeyaml.Yaml");
                this.instance = clazz.getConstructor().newInstance();
                this.load = clazz.getMethod("load", java.io.Reader.class);
            } catch (Throwable t) {
                throw new IllegalStateException("缺少 SnakeYAML（请把 snakeyaml.jar 加入 -cp，"
                        + "或使用 .verify-libs/snakeyaml-2.2.jar）", t);
            }
        }

        Object load(java.io.Reader reader) {
            try {
                return load.invoke(instance, reader);
            } catch (Throwable t) {
                throw new IllegalStateException("SnakeYAML 解析失败: " + t.getCause(), t);
            }
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private static List<String> strList(Object node) {
        List<String> out = new ArrayList<>();
        if (node instanceof List<?> list) {
            list.forEach(v -> out.add(String.valueOf(v)));
        } else if (node != null) {
            out.add(String.valueOf(node));
        }
        return out;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static double num(Object value, double def) {
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

    private static void error(String file, String message) {
        errors++;
        System.out.println("[错误] " + file + " -> " + message);
    }

    private static void warning(String file, String message) {
        warnings++;
        System.out.println("[警告] " + file + " -> " + message);
    }
}
