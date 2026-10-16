package com.advancedenchants.verify;

import com.advancedenchants.core.EnchantLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 枚举名审计：把配置里用到的 PARTICLE / SOUND / POTION / ENTITY / MATERIAL 名称
 * 与当前编译所用 Paper API 的枚举常量做对照，列出不存在的名字。
 *
 * 用法：
 *   java -cp "target/classes;&lt;paper-api.jar&gt;;&lt;adventure+snakeyaml&gt;" \
 *        com.advancedenchants.verify.EnumAudit src/main/resources/enchants
 */
public final class EnumAudit {

    /** 已知的跨版本重命名：旧名 -> 新名（1.20.5 起生效），仅用于提示。 */
    private static final Map<String, String> PARTICLE_RENAMES = Map.ofEntries(
            Map.entry("EXPLOSION_NORMAL", "POOF"),
            Map.entry("EXPLOSION_LARGE", "EXPLOSION"),
            Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
            Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"),
            Map.entry("REDSTONE", "DUST"),
            Map.entry("SPELL_MOB", "ENTITY_EFFECT"),
            Map.entry("SPELL_MOB_AMBIENT", "ENTITY_EFFECT"),
            Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"),
            Map.entry("SPELL_WITCH", "WITCH"),
            Map.entry("WATER_BUBBLE", "BUBBLE"),
            Map.entry("WATER_SPLASH", "SPLASH"),
            Map.entry("WATER_WAKE", "BUBBLE_POP"),
            Map.entry("SMOKE_NORMAL", "SMOKE"),
            Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"),
            Map.entry("TOTEM", "TOTEM_OF_UNDYING"),
            Map.entry("SNOWBALL", "ITEM_SNOWBALL"),
            Map.entry("SLIME", "ITEM_SLIME"),
            Map.entry("BLOCK_CRACK", "BLOCK"),
            Map.entry("BLOCK_DUST", "BLOCK"),
            Map.entry("DRIP_LAVA", "DRIPPING_LAVA"),
            Map.entry("DRIP_WATER", "DRIPPING_WATER"),
            Map.entry("CRIT_MAGIC", "ENCHANTED_HIT"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"),
            Map.entry("MOB_APPEARANCE", "ELDER_GUARDIAN"),
            Map.entry("FOOTSTEP", "BLOCK"));

    private static final Map<String, String> POTION_RENAMES = Map.of(
            "DAMAGE_RESISTANCE", "RESISTANCE",
            "SLOW_DIGGING", "MINING_FATIGUE",
            "JUMP", "JUMP_BOOST",
            "CONFUSION", "NAUSEA",
            "HARM", "INSTANT_DAMAGE",
            "HEAL", "INSTANT_HEALTH");

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "src/main/resources/enchants");
        Set<String> particles = new LinkedHashSet<>();
        Set<String> sounds = new LinkedHashSet<>();
        Set<String> potions = new LinkedHashSet<>();
        Set<String> entities = new LinkedHashSet<>();
        Set<String> materials = new LinkedHashSet<>();

        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            System.out.println("目录不存在: " + dir);
            System.exit(2);
        }
        YamlLite yaml = new YamlLite();
        for (File file : files) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                collect(yaml.load(reader), particles, sounds, potions, entities, materials);
            }
        }

        System.out.println("── 配置中使用到的枚举名 ──");
        System.out.println("PARTICLE: " + particles.size() + " 种");
        System.out.println("SOUND:    " + sounds.size() + " 种");
        System.out.println("POTION:   " + potions.size() + " 种");
        System.out.println("ENTITY:   " + entities.size() + " 种");
        System.out.println("MATERIAL: " + materials.size() + " 种");

        int missing = 0;
        missing += report("PARTICLE", particles, org.bukkit.Particle.class, PARTICLE_RENAMES);
        missing += report("SOUND", sounds, org.bukkit.Sound.class, Map.of());
        missing += report("ENTITY", entities, org.bukkit.entity.EntityType.class, Map.of());
        missing += report("MATERIAL", materials, org.bukkit.Material.class, Map.of());
        System.out.println("── 当前 API 中不存在的名字: " + missing + " ──");

        System.out.println("── POTION 名称（运行时按 Registry + 别名解析，不参与上面的存在性判定）──");
        System.out.println(potions);

        System.exit(0);
    }

    private static <E extends Enum<E>> int report(String label, Set<String> used, Class<E> type,
                                                  Map<String, String> renames) {
        Set<String> constants = readEnumConstants(type);
        List<String> missing = new ArrayList<>();
        Map<String, String> renameHits = new TreeMap<>();
        List<String> aliased = new ArrayList<>();
        for (String name : used) {
            String upper = name.toUpperCase(Locale.ROOT);
            if (constants.contains(upper)) {
                continue;
            }
            // 运行时别名（ParticleAliases / VersionSafe）能救回来的不算缺失
            String alias = com.advancedenchants.util.ParticleAliases.alias(upper);
            if (alias != null && constants.contains(alias.toUpperCase(Locale.ROOT))) {
                aliased.add(name + "->" + alias);
                continue;
            }
            missing.add(name);
            String renamed = renames.get(upper);
            if (renamed != null && constants.contains(renamed)) {
                renameHits.put(name, renamed);
            }
        }
        System.out.println("── " + label + " ──");
        if (missing.isEmpty()) {
            System.out.println("  全部可用 ✓（该枚举共 " + constants.size() + " 个常量）");
        } else {
            System.out.println("  当前 API 不存在: " + missing);
            if (!renameHits.isEmpty()) {
                System.out.println("  其中 1.20.5 已重命名（需改名）: " + renameHits);
            }
        }
        if (!aliased.isEmpty()) {
            System.out.println("  通过运行时别名自动回退（" + aliased.size() + " 项）: " + aliased);
        }
        return missing.size();
    }

    /**
     * 直接从 class 文件解析枚举常量名。
     * 这样审计脚本不依赖运行时的 Guava 等库，也不需要真正加载 Bukkit 类。
     */
    private static Set<String> readEnumConstants(Class<?> type) {
        Set<String> out = new LinkedHashSet<>();
        try {
            String resource = type.getName().replace('.', '/') + ".class";
            java.io.InputStream in = type.getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                return out;
            }
            byte[] bytes = in.readAllBytes();
            in.close();
            java.io.DataInputStream data = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
            data.readInt();               // magic
            data.readUnsignedShort();     // minor
            data.readUnsignedShort();     // major
            int poolCount = data.readUnsignedShort();
            String[] utf8 = new String[poolCount];
            int[] classNameIndex = new int[poolCount];
            for (int i = 1; i < poolCount; i++) {
                int tag = data.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = data.readUTF();
                    case 7, 8, 16, 19, 20 -> classNameIndex[i] = data.readUnsignedShort();
                    case 15 -> {
                        data.readUnsignedByte();
                        data.readUnsignedShort();
                    }
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> {
                        data.readUnsignedShort();
                        data.readUnsignedShort();
                    }
                    case 5, 6 -> {
                        data.readLong();
                        i++;
                    }
                    default -> {
                        return out;
                    }
                }
            }
            data.readUnsignedShort();     // access flags
            data.readUnsignedShort();     // this class
            data.readUnsignedShort();     // super class
            int interfaces = data.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) {
                data.readUnsignedShort();
            }
            int fields = data.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                int access = data.readUnsignedShort();
                int nameIndex = data.readUnsignedShort();
                data.readUnsignedShort();             // descriptor
                int attributes = data.readUnsignedShort();
                boolean isEnumConstant = (access & 0x4000) != 0;
                for (int a = 0; a < attributes; a++) {
                    data.readUnsignedShort();
                    int length = data.readInt();
                    data.skipBytes(length);
                }
                String name = utf8[nameIndex];
                if (isEnumConstant && name != null) {
                    out.add(name);
                }
            }
        } catch (Throwable ignored) {
            // 解析失败则返回已收集到的部分
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object node, Set<String> particles, Set<String> sounds,
                                Set<String> potions, Set<String> entities, Set<String> materials) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                Object value = e.getValue();
                if (value instanceof String s && !s.isBlank()) {
                    switch (key) {
                        case "particle" -> particles.add(s.toUpperCase(Locale.ROOT));
                        case "sound" -> sounds.add(s.toUpperCase(Locale.ROOT));
                        case "potion" -> potions.add(s.toUpperCase(Locale.ROOT));
                        case "entity" -> entities.add(s.toUpperCase(Locale.ROOT));
                        case "material" -> materials.add(s.toUpperCase(Locale.ROOT));
                        default -> {
                            // 忽略
                        }
                    }
                }
                collect(value, particles, sounds, potions, entities, materials);
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) {
                collect(o, particles, sounds, potions, entities, materials);
            }
        }
    }

    /** 最小 YAML 读取封装（反射调用 SnakeYAML，避免编译期依赖）。 */
    private static final class YamlLite {
        private final Object instance;
        private final java.lang.reflect.Method load;

        YamlLite() {
            try {
                Class<?> clazz = Class.forName("org.yaml.snakeyaml.Yaml");
                this.instance = clazz.getConstructor().newInstance();
                this.load = clazz.getMethod("load", java.io.Reader.class);
            } catch (Throwable t) {
                throw new IllegalStateException("缺少 SnakeYAML（把 .verify-libs/snakeyaml-2.2.jar 加入 -cp）", t);
            }
        }

        Object load(java.io.Reader reader) throws Exception {
            return load.invoke(instance, reader);
        }
    }

    static {
        // 触达白名单常量，确保与主校验器使用同一份定义
        if (EnchantLoader.Actions.KNOWN.isEmpty()) {
            throw new IllegalStateException("动作白名单为空");
        }
    }
}
