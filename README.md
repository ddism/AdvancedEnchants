# 更多的附魔

**配置驱动的 Paper 自定义附魔引擎** —— 内置 **129 条**分级附魔，覆盖剑 / 斧 / 三叉戟 / 弓 / 弩 / 盔甲 / 盾牌 / 镐 / 锹 / 锄 / 剪刀 / 钓鱼竿 / 鞘翅 / 任意物品，并且**所有附魔（含你自己设计的）都写在 YAML 里**，改配置 + `/ae reload` 即可生效，不需要写 Java、不需要重新编译。

```
┌──────────────────────────────┐
│  YAML 配置（enchants/*.yml）  │  ← 服主/玩家在这里设计附魔
└──────────────┬───────────────┘
               │ EnchantLoader 解析 + 校验
┌──────────────▼───────────────┐
│  EnchantRegistry 注册表       │  ← 定义 + 服务端注册结果
└──────────────┬───────────────┘
               │ 事件
┌──────────────▼───────────────┐
│  TriggerListener 触发器总线   │  ← 26 种触发器，把 Bukkit 事件翻译为上下文
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  Engine 条件判定 + 冷却/概率   │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  ActionExec 动作库（46 种）    │  ← 伤害/状态/世界/物品/反馈
└──────────────────────────────┘
```

---

## 1. 快速开始

```bash
# 1) 构建（默认编译目标 paper-api 1.20.4，产物可在 1.20.4 ~ 1.21.x 运行）
mvn clean package
# 外网可用时可直接按目标版本编译：
mvn -Ppaper-1.21.4 clean package

# 没有 Maven / 完全离线：用这个脚本（javac + jar，自动定位本地依赖，约 3 秒出包）
build-no-maven.cmd

# 2) 部署
将 dist/AdvancedEnchants-1.0.0.jar 丢进服务端 plugins/ 目录，重启

# 3) 首次启动会自动生成
plugins/AdvancedEnchants/
├── config.yml
└── enchants/
    ├── 10-common.yml   (25)   ├── 40-epic.yml      (21)
    ├── 20-uncommon.yml (31)   ├── 50-legendary.yml (12)
    ├── 30-rare.yml     (30)   ├── 60-mythic.yml    (10)
    └── custom.yml      ← 你的自定义附魔写这里（升级不覆盖）
```

## 2. 指令与权限

| 指令 | 说明 | 权限（默认） |
|---|---|---|
| `/ae` | 帮助与状态 | `advancedenchants.use` (true) |
| `/ae list [等级]` | 列出附魔（可按 COMMON~MYTHIC 过滤） | `advancedenchants.admin` (op) |
| `/ae info <ID>` | 查看单条附魔定义（触发点/概率/冷却/动作数） | `advancedenchants.admin` |
| `/ae give <ID> [等级] [玩家]` | 发放附魔书 | `advancedenchants.admin` |
| `/ae enchant <ID> [等级]` | 给手持物品附魔（会校验适用类型） | `advancedenchants.use` |
| `/ae clear` | 清除手持物品的自定义附魔 | `advancedenchants.use` |
| `/ae inspect` | 查看手持物品的附魔数据与灵魂数 | `advancedenchants.use` |
| `/ae reload` | 重载 config.yml 与全部附魔文件 | `advancedenchants.admin` |
| `/ae debug` | 开关触发调试日志（输出到控制台） | `advancedenchants.admin` |

别名：`/ae`、`/aench`、`/advench`。

## 3. 附魔体系

### 3.1 六个等级

| 等级 | 中文 | 颜色 | 默认权重 | 条数 |
|---|---|---|---|---|
| COMMON | 普通 | 灰 | 100 | 25 |
| UNCOMMON | 优秀 | 绿 | 55 | 31 |
| RARE | 稀有 | 蓝 | 26 | 30 |
| EPIC | 史诗 | 紫 | 10 | 21 |
| LEGENDARY | 传说 | 金 | 4 | 12 |
| MYTHIC | 神话 | 红 | 1 | 10 |

完整清单（ID、中文名、等级、适用类型、效果）见 **[docs/ENCHANT_ROSTER.md](docs/ENCHANT_ROSTER.md)**。

### 3.2 类型覆盖

剑 26 · 盔甲 27 · 斧 16 · 弓 16 · 任意 15 · 镐 12 · 弩 10 · 钓鱼竿 10 · 锹 9 · 鞘翅 7 · 锄 6 · 三叉戟 4 · 盾牌 2 · 剪刀 1（同一条可属多类型，故总和大于 129）。

### 3.3 能力清单

- **触发器 26 种**：`ON_HIT` `ON_KILL` `ON_DAMAGED` `ON_DEATH` `ON_BLOCK_BREAK` `ON_BLOCK_PLACE` `ON_MINE_TICK` `ON_SHOOT` `ON_PROJECTILE_HIT` `ON_FISH` `ON_JUMP` `ON_FALL_DAMAGE` `ON_MOVE` `ON_SNEAK` `ON_BOW_CHARGE` `ON_SHIELD_BLOCK` `ON_INTERACT` `ON_ANVIL_COMBINE` `ON_ENCHANT` `ON_EQUIP` `ON_CONSUME` `ON_SWAP_HAND` `PASSIVE` `ON_LOOT` `ON_XP_GAIN` `ON_DISABLE`
- **条件 26 种**：概率 / 生命比 / 目标生命比 / 潜行 / 疾跑 / 地面 / 水中 / 下雨 / 着火 / 时间 / Y 坐标 / 世界 / 生物群系 / 方块 / 目标类型 / 药水 / 权限 / 灵魂数 / 已有附魔 / 昼夜 / 难度 / 弹射距离 / 拉弓力度 / 附近队友 …
- **动作 44 种**：乘算伤害 / 加算伤害 / 真实伤害 / 百分比生命伤害 / 处决 / 吸血 / 治疗 / 减伤 / 闪避 / 伤害上限 / 反弹 / 易伤标记 / 破甲 / 掉落倍增与转换 / 药水 / 点燃 / 击退 / 击飞 / 拉拽 / 传送 / 召唤 / 闪电 / 爆炸 / 药水云 / 发射弹射物 / 箭雨 / 冲击波 / 冰冻 / 放置/破坏方块 / 连锁挖矿 / 连锁砍树 / 范围剪羊毛 / 自动补种 / 自动熔炼 / 耐久 / 灵魂 / 灵魂绑定 / 取消事件 / 濒死保留 / 攻击冷却 / 消耗手持 / 命令 / 消息 / 标题 / 动作栏 / 粒子 / 音效 / 经验倍率与直给 …（实测 129 条附魔共使用 44 种动作、160 个触发器块）
- **数值公式**：`(base + per-level × (等级-1)) × 缩放系数`，缩放支持 `LINEAR / QUADRATIC / EXPONENTIAL / DIMINISHING`，并可用 `max` 钳制。

## 4. 自己设计一条附魔（3 分钟）

编辑 `plugins/AdvancedEnchants/enchants/custom.yml`，执行 `/ae reload`：

```yaml
enchants:
  my_lucky_star:
    display: "&#FFD700幸运星"          # 显示名（支持 & 与 &#RRGGBB）
    tier: EPIC                        # COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC
    max-level: 4
    types: [PICKAXE, SHOVEL]          # 适用类型
    description:
      - "&7挖掘时有 &f{value}%% &7几率额外掉落"   # {value} = 首个动作当前等级数值
    triggers:
      ON_BLOCK_BREAK:
        chance: 25                    # 触发概率（%）
        chance-per-level: 10
        conditions:                   # 全部满足才触发（AND）
          - type: BLOCK
            value: ["*_ORE"]
        actions:                      # 依次执行
          - type: MULTIPLY_DROP
            base: 2
            per-level: 0.5
            max: 4
          - type: PARTICLE
            particle: HAPPY_VILLAGER
            count: 8
            who: BLOCK
          - type: SOUND
            sound: ENTITY_EXPERIENCE_ORB_PICKUP
            volume: 0.5
```

同一触发点多套参数用列表式写法：

```yaml
    triggers:
      ON_HIT:
        - conditions:
            - type: HEALTH
              value: 0.3
              mode: BELOW
          actions:
            - type: DAMAGE_MULTIPLY
              base: 1.8
        - actions:
            - type: PARTICLE
              particle: CRIT
              count: 5
```

完整字段表见 **[docs/SCHEMA.md](docs/SCHEMA.md)**（触发器 / 条件 / 动作的全部合法值与字段名）。

## 5. 游戏内获取附魔

| 途径 | 说明 |
|---|---|
| 附魔书 | `/ae give <ID> [等级]`，与铁砧叠加可升级（受 `max-level` 限制） |
| 铁砧 | 左侧放带该附魔的物品、右侧放同名附魔书即可升级 |
| 磨刀石 | 可磨掉自定义附魔（`config.yml` 的 `grindstone.strip-enabled`） |
| 指令 | `/ae enchant <ID> [等级]` 直接给手持物品附加 |
| 数据存储 | 附魔数据存于物品 NBT（PersistentDataContainer），重启/跨服/潜影盒都不会丢 |

> 在 1.21.x 上，插件会通过 `Registry#register` 把附魔真正注册进服务端，因此物品 tooltip 与附魔光效都会正常显示；在旧版本或注册失败时自动降级为 **Lore 显示模式**，效果完全不受影响。

## 6. 配置文件

`config.yml` 关键项：

| 配置 | 默认 | 说明 |
|---|---|---|
| `max-enchants-per-item` | 12 | 单件物品最多附魔条数 |
| `passive-interval-ticks` | 20 | `PASSIVE` 触发器检查间隔 |
| `allow-terrain-changes` | true | 是否允许连锁挖矿/爆破等改地形 |
| `allow-commands` | **false** | 是否允许 `COMMAND` 动作（防提权，默认关闭） |
| `protect-block-containers` | true | 范围内保护箱子/熔炉等容器 |
| `debug` | false | 触发日志 |

## 7. 开发与自检

```bash
# 编译
mvn clean package
# 离线/无 Maven 时（javac + JDK jar，自动定位本地依赖）：
build-no-maven.cmd

# —— 不启动服务端也能跑的静态自检（推荐每次改完 YAML 都跑）——
# 1) 配置校验：解析 YAML + 校验字段/白名单/安全规则
java -cp "target/classes;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.YamlVerifier src/main/resources/enchants

# 2) 枚举名审计：检查 PARTICLE / SOUND / ENTITY / MATERIAL 名称在目标 API 中是否存在
#    （需要 paper-api 在 classpath 上）
java -cp "target/classes;<paper-api.jar>;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.EnumAudit src/main/resources/enchants
```

最近一次自检结果：

| 检查 | 结果 |
|---|---|
| `javac --release 21` 编译（paper-api 1.20.4） | ✅ 0 错误，36 个 class |
| `build-no-maven.cmd` 端到端 | ✅ 编译 → 复制资源 → 打包，产出 `dist/AdvancedEnchants-1.0.0.jar`（57 条目 / 151 KB） |
| `jar tf` 结构校验 | ✅ 全部条目前斜杠规范，含 `plugin.yml` + `config.yml` + `enchants/`7 个文件 |
| 用**打包后的 jar** 加载校验器类跑 YAML 校验 | ✅ PASS（证明 jar 内字节码可正常加载） |
| `YamlVerifier` | ✅ PASS：7 个文件 / 129 条唯一 ID / 160 个触发器块 / 0 错误 0 警告 |
| 独立红队评审 | ✅ 契约 129/129 一致；发现的问题已修，逐条记录见 [docs/FIXES.md](docs/FIXES.md) |

> ⚠️ 尚未在真实服务端启动过（本机无 Paper 服务端）。首次上测试服请检查控制台是否有
> `未知药水效果` / `不支持 Particle` 告警，以及 `/ae list` 是否显示 129 条。
| `EnumAudit` | ✅ SOUND / ENTITY / MATERIAL 全部存在；PARTICLE 的 13 个名字差异已由 `ParticleAliases` 自动映射 |

### 目录结构

```
AdvancedEnchants/
├── pom.xml
├── build-no-maven.cmd       # 离线打包脚本（javac + jar）
├── README.md
├── dist/
│   └── AdvancedEnchants-1.0.0.jar   # 可直接放进 plugins/
├── docs/
│   ├── SCHEMA.md            # 配置规范（触发器/条件/动作全字段）
│   ├── ENCHANT_ROSTER.md    # 129 条附魔总表（契约文件）
│   └── FIXES.md             # 独立评审发现的问题与修复记录
├── .verify-libs/            # 自检脚本用的依赖（不打包进插件）
└── src/main/
    ├── java/com/advancedenchants/
    │   ├── AdvancedEnchants.java      # 主类：生命周期、注册、任务
    │   ├── command/EnchantCommands.java
    │   ├── core/    EnchantLoader · EnchantRegistry · Engine · ActionExec
    │   │            Conditions · TriggerCtx · EventMods
    │   ├── listener/TriggerListener.java   # 26 种 Bukkit 事件 → 触发器
    │   ├── model/   EnchantDef · TriggerDef · CondDef · ActionDef · Tier
    │   │            ApplyType · MaterialTypes · Levels · Trigger
    │   ├── storage/ ItemTags（PDC/NBT）· LoreRenderer（Lore 渲染）
    │   ├── util/    VersionSafe（跨版本适配）· ParticleAliases · Messages
    │   └── verify/  YamlVerifier · EnumAudit（静态自检工具）
    └── resources/
        ├── plugin.yml · config.yml
        └── enchants/*.yml
```

## 8. 跨版本与已知限制

**版本策略**：以 paper-api **1.20.4** 编译，运行支持 **1.20.4 ~ 1.21.x**。原因是 1.20.4 的 API 是 1.21.x 的子集；需要 1.21 专有 API 的地方（`Registry#register` 注册自定义附魔、`setBasePotionType`、Adventure 消息重载）全部走 `util/VersionSafe` 与反射适配，失败时优雅降级而不是崩溃。

**已知限制**（配置层做了近似实现，均不影响正常游玩）：

| 限制 | 现状 |
|---|---|
| 条件之间只有 AND，没有 OR | 需要互斥分支时拆成多个触发器（如 `ON_HIT` + `ON_PROJECTILE_HIT`） |
| 部分效果无专用动作 | 例如「流血 DoT」用 `POTION POISON`、「范围溅射」用 `SHOCKWAVE`、「压缩成块」用 `CONVERT_DROP` |
| 无领地/区域插件联动 | 破坏类动作内置容器保护与 `limit` 限流 + `allow-terrain-changes` 总开关；建议配合领地插件使用 |
| `RUN_SCRIPT` | 仅预留接口，默认关闭 |
| 药水 `duration` 单位 | **tick**（20 = 1 秒）；`IGNITE` 的 `base` 单位是**秒** |
| 粒子/音效名称 | 跨版本重命名由 `ParticleAliases` 自动映射；填错会在控制台告警并跳过该效果 |
| Folia | 未适配（`folia-supported: false`）—— 引擎依赖主线程事件模型 |

## 9. 许可

本工程为你自行持有与使用的插件源码，可自由修改、二次发布。
