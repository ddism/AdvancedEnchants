# AdvancedEnchants 附魔配置规范（Schema v1.1）

> 本文件定义 `resources/enchants/*.yml` 的**唯一合法写法**。Java 实现（`EnchantLoader`、`ActionExec`、条件求值器）严格按此规范解析；配置写错只会在控制台输出一条带路径的警告并跳过该条目，不会崩服。
>
> **v1.1 变更**：触发器支持列表式多块（解决 YAML 重复键限制）；新增条件 `FORCE`、`NEARBY_ALLIES`；新增动作 `SOULBOUND`、`ATTACK_SPEED`、`ADD_XP`、`MULTIPLY_XP`；明确 `POTION.duration` 单位为 **tick**、`IGNITE.base` 单位为 **秒**。

---

## 1. 文件与结构

```
plugins/AdvancedEnchants/enchants/
├── 10-common.yml      # 普通（内置，可删可改）
├── 20-uncommon.yml    # 优秀
├── 30-rare.yml        # 稀有
├── 40-epic.yml        # 史诗
├── 50-legendary.yml   # 传说
├── 60-mythic.yml      # 神话
├── 90-tools-fishing.yml
└── custom.yml         # ★ 你的自定义附魔放这里（升级不覆盖）
```

每个文件顶层都是同一个 key：

```yaml
enchants:
  <附魔ID>:
    # 定义（见 2）
```

* **ID 是主键**：全局唯一，正则 `^[a-z0-9_]{2,40}$`，最终注册为 `advancedenchants:<id>`。
* 同一 ID 后加载的会覆盖先加载的，并在控制台给出 `[覆盖]` 警告；`custom.yml` 永远最后加载，因此**你的配置可以覆盖内置附魔**。
* 改完执行 `/ae reload` 生效，无需重启。

---

## 2. 定义字段

```yaml
enchants:
  lifesteal:
    display: "&c吸血"                # 必填，物品 Lore/提示显示名，支持 & 与 &#RRGGBB
    tier: COMMON                     # 必填：COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC
    max-level: 3                     # 必填，1~10
    types: [SWORD, AXE]              # 必填，适用类型（不少于 1 个）
    description:                     # 可选，多行；支持 {level} {value} 占位符
      - "&7造成伤害的 &f{value}% &7回复自身生命"
    conflicts: [vampiric_aura, soul_eater]   # 可选，互斥附魔 ID（铁砧/附魔台不会同时出现）
    rarity-weight: 10                # 可选，附魔台/战利品随机权重，默认按 tier 推导
    enabled: true                    # 可选，默认 true；false 表示不注册（用于临时关闭）
    triggers:                        # 必填，至少 1 个；同一触发点可写多个块
      ON_HIT:
        cooldown: 0                  # 可选，该触发点的冷却（秒，支持小数）；0 = 无冷却
        chance: 100                  # 可选，触发概率（%），默认 100
        chance-per-level: 0          # 可选，每级额外概率（%）
        conditions:                  # 可选，全部满足才执行
          - type: CHANCE
            value: 25                # 25% 概率
        actions:                     # 必填，至少 1 个
          - type: LIFESTEAL
            base: 2                  # 等级 1 时的数值
            per-level: 1.5           # 每提升 1 级额外增加
            scale: LINEAR            # 可选：LINEAR(默认)/QUADRATIC/EXPONENTIAL/DIMINISHING
            max: 12                  # 可选，数值上限（钳制）
```

### 2.1 `types` 合法值

| 值 | 含义 |
|---|---|
| `SWORD` | 剑 |
| `AXE` | 斧 |
| `TRIDENT` | 三叉戟 |
| `BOW` | 弓 |
| `CROSSBOW` | 弩 |
| `ARMOR` | 头盔 / 胸甲 / 护腿 / 靴子 / 海龟壳 |
| `SHIELD` | 盾牌 |
| `PICKAXE` | 镐 |
| `SHOVEL` | 锹 |
| `HOE` | 锄 |
| `SHEARS` | 剪刀 |
| `FISHING_ROD` | 钓鱼竿 |
| `ELYTRA` | 鞘翅 |
| `ALL` | 任意物品（谨慎使用，会影响所有手持物） |

> 判定规则：附魔效果只从**当前触发上下文相关的装备槽**读取（攻击=主手，被攻击=全部盔甲+副手，挖掘=主手，钓鱼=主手，滑翔=胸甲）。

### 2.2 等级数值公式

```text
scaled(level) = (base + per-level * (level - 1)) * modeFactor(level, maxLevel)
modeFactor: LINEAR      = 1
            QUADRATIC   = 1 + 0.25 * (level - 1)
            EXPONENTIAL = 1.6 ^ (level - 1)      # 高风险高收益，单级翻倍级增长
            DIMINISHING = 1 + 0.6 * (level - 1) / maxLevel
最终数值 = min(scaled, max)  并且永不低于 0
```

`{value}` 占位符 = 该附魔**第一个 action** 在**当前等级**下的计算值（保留 2 位小数）。

---

## 3. 触发器 `triggers`

| 触发器 | 触发时机 | 上下文可用键 |
|---|---|---|
| `ON_HIT` | 你攻击命中实体 | `attacker player`, `victim living`, `damage` |
| `ON_KILL` | 你击杀实体 | `attacker player`, `victim living` |
| `ON_DAMAGED` | 你受到实体伤害 | `attacker player?`, `victim player`, `damage` |
| `ON_DEATH` | 你死亡 | `victim player` |
| `ON_BLOCK_BREAK` | 你破坏方块 | `player`, `block`, `blockType` |
| `ON_BLOCK_PLACE` | 你放置方块 | `player`, `block`, `blockType` |
| `ON_MINE_TICK` | 破坏进度变化（0.1 秒粒度，慎用高频动作） | `player`, `block` |
| `ON_SHOOT` | 你射出弹射物 | `player`, `projectile` |
| `ON_PROJECTILE_HIT` | 你的弹射物命中 | `player`, `projectile`, `target?`, `block?`, `distance` |
| `ON_FISH` | 钓鱼状态变化（咬钩/收竿/钓到） | `player`, `caught?` |
| `ON_JUMP` | 你跳跃 | `player` |
| `ON_FALL_DAMAGE` | 你受到坠落伤害 | `player`, `damage` |
| `ON_MOVE` | 移动（**仅允许条件判断类轻量动作**，禁止生成实体/粒子） | `player`, `from`, `to` |
| `ON_SNEAK` | 开始潜行 | `player` |
| `ON_BOW_CHARGE` | 拉弓中（每 5 tick） | `player`, `force` |
| `ON_SHIELD_BLOCK` | 举盾格挡伤害 | `player`, `damage` |
| `ON_INTERACT` | 右键交互（空气/方块/实体） | `player`, `block?`, `target?` |
| `ON_ANVIL_COMBINE` | 铁砧合成出结果 | `player`, `left`, `right`, `result` |
| `ON_ENCHANT` | 附魔台附魔成功 | `player`, `item` |
| `ON_EQUIP` | 装备变化（穿上/脱下） | `player`, `item` |
| `ON_CONSUME` | 使用/吃掉物品完成 | `player`, `item` |
| `ON_SWAP_HAND` | 切换主副手 | `player` |
| `PASSIVE` | 每 1 秒周期性检查（适合光环/常驻效果） | `player` |
| `ON_LOOT` | 方块掉落物生成（可增删掉落） | `player`, `block`, `drops` |
| `ON_XP_GAIN` | 获得经验（可倍增） | `player`, `amount` |
| `ON_DISABLE` | 插件卸载/重载（用于清理） | `player` |

> 未列出的触发器名 = 配置错误，加载时告警并忽略。

---

## 4. 条件 `conditions`

**全部条件必须同时满足**（AND）。数值比较类支持 `value`（等级 1 基准）与 `per-level`。

| type | 字段 | 含义 |
|---|---|---|
| `CHANCE` | `value`, `per-level` | 概率百分百通过 |
| `HEALTH` | `value`(0~1), `per-level`, `mode: BELOW/ABOVE` | 自身生命比例比较 |
| `TARGET_HEALTH` | `value`(0~1), `mode: BELOW/ABOVE` | 目标生命比例比较 |
| `SNEAK` | `value: true/false` | 是否潜行 |
| `SPRINT` | `value: true/false` | 是否疾跑 |
| `ON_GROUND` | `value` | 是否在地面 |
| `IN_WATER` | `value` | 是否在水中 |
| `IN_RAIN` | `value` | 所在世界是否下雨/雷暴 |
| `BURNING` | `value` | 自身是否着火 |
| `TARGET_BURNING` | `value` | 目标是否着火 |
| `TIME` | `min`, `max`(0~24000) | 世界时间区间 |
| `Y` | `min`, `max` | Y 坐标区间 |
| `WORLD` | `value: [world, world_nether]` | 世界白名单（支持 `*` 通配） |
| `BIOME` | `value: [plains, desert]` | 生物群系白名单（支持 `*`） |
| `BLOCK` | `value: [DIAMOND_ORE]`, `blacklist: false` | 上下文方块类型（挖矿/放置类生效） |
| `TARGET_TYPE` | `value: [PLAYER, ZOMBIE]`, `blacklist: false` | 目标实体类型 |
| `HAS_POTION` | `value: [POISON]`, `min-level: 1` | 自身是否拥有指定药水效果 |
| `PERMISSION` | `value: "ae.enchant.xxx"` | 玩家权限（`!` 前缀取反） |
| `SOULS` | `value`(下限), `per-level` | 物品已累积灵魂数 |
| `HAS_ENCHANT` | `value: "sharpness"`, `min-level: 1` | 上下文物品是否含某附魔 |
| `DAY` / `NIGHT` | 无 | 白天 / 夜晚 |
| `DIFFICULTY` | `value: [HARD]` | 世界难度 |
| `PROJECTILE_DISTANCE` | `min`, `max`, `per-level` | 箭矢飞行距离（格） |
| `FORCE` | `value`(0~1 下限), `per-level` | 拉弓力度（`ON_SHOOT` / `ON_BOW_CHARGE` 上下文） |
| `NEARBY_ALLIES` | `min`, `max`, `radius` | 附近队友（同队玩家）数量区间 |

### 4.1 同一触发点多套参数（v1.1）

YAML 不允许同名键重复，因此**同触发器多套参数必须写成列表**：

```yaml
    triggers:
      ON_HIT:
        - conditions:
            - type: HEALTH
              value: 0.5
              mode: BELOW
          actions:
            - type: DAMAGE_MULTIPLY
              base: 1.5
        - actions:                 # 第二套参数，无条件
            - type: PARTICLE
              particle: CRIT
              count: 5
```

两种写法都合法：直接字段（`ON_HIT:` 下直接写 `actions:`）或列表式（推荐）。

---

## 5. 动作 `actions`

通用字段：`type`（必填）、`base`、`per-level`、`scale`、`max`。含 `BASE`/`PER_LEVEL` 的字段名会在表中单独标注。

### 5.1 伤害与目标

| type | 专有字段 | 说明 |
|---|---|---|
| `DAMAGE_MULTIPLY` | `base`(倍率，1=不变), `per-level` | 乘算伤害 |
| `DAMAGE_ADD` | `base`, `per-level` | 加算伤害（按倍率，0.1 = 半颗心） |
| `TRUE_DAMAGE` | `base`, `per-level` | 无视护甲的真实伤害 |
| `PERCENT_CURRENT_HP` | `base`(0~1), `per-level` | 按目标当前生命百分比造成伤害 |
| `PERCENT_MAX_HP` | `base`(0~1), `per-level` | 按目标最大生命百分比造成伤害 |
| `EXECUTE` | `base`(0~1 阈值), `per-level`, `damage` | 目标低于阈值则额外伤害 |
| `LIFESTEAL` | `base`(0~1 比例), `per-level` | 按已造成伤害比例回复自身 |
| `HEAL` | `base`(心), `per-level` | 直接回血 |
| `DAMAGE_REDUCE` | `base`(0~1 比例), `per-level` | 降低本次受到的伤害 |
| `DODGE` | `base`(0~1 概率), `per-level` | 取消本次伤害（完全闪避） |
| `DAMAGE_CAP` | `base`(最大伤害值) | 把本次伤害钳制到上限 |
| `REFLECT` | `base`(0~1 比例), `per-level` | 反弹伤害给攻击者 |
| `MARK` | `base`(0~1 易伤比例), `per-level`, `duration`, `duration-per-level` | 标记目标，期间受额外伤害 |
| `ARMOR_DAMAGE` | `base`(耐久点), `per-level` | 破坏目标护甲耐久 |
| `ARMOR_PIERCE` | `base`(0~1), `per-level` | 忽略目标一部分护甲减伤 |
| `CONVERT_DROP` | `base`(0~1 概率), `per-level`, `material` | 把掉落物转换为指定物品 |
| `MULTIPLY_DROP` | `base`(倍率), `per-level`, `max` | 掉落数量倍率 |

### 5.2 实体与世界

| type | 专有字段 | 说明 |
|---|---|---|
| `POTION` | `potion`, `duration`, `duration-per-level`, `amplifier`, `amplifier-per-level`, `who: SELF/TARGET` | 施加药水 |
| `CLEAR_POTION` | `potion`, `who` | 清除药水 |
| `IGNITE` | `base`(秒), `per-level`, `who` | 点燃 |
| `EXTINGUISH` | `who` | 灭火 |
| `KNOCKBACK` | `base`(强度), `per-level`, `who` | 击退 |
| `LAUNCH` | `base`(向上速度), `per-level`, `who` | 击飞 |
| `PULL` | `base`(距离), `per-level`, `who` | 拉向玩家 |
| `TELEPORT` | `base`(距离), `target: TARGET/ARROW/BEHIND` | 位移 |
| `SUMMON` | `entity`, `count`, `count-per-level`, `lifetime` | 召唤实体（自动清理/不爆炸破坏） |
| `LIGHTNING` | `base`(0~1 概率), `damage`(是否造成伤害) | 召唤闪电 |
| `EXPLOSION` | `base`(半径), `per-level`, `set-fire`, `break-blocks` | 爆炸（默认不破坏地形） |
| `POTION_CLOUD` | `potion`, `duration`, `radius`, `amplifier` | 生成药水云 |
| `SPAWN_PROJECTILE` | `projectile`, `count`, `count-per-level`, `speed`, `spread` | 发射弹射物 |
| `ARROW_RAIN` | `count`, `radius`, `speed`, `damage` | 箭雨 |
| `SHOCKWAVE` | `radius`, `damage`, `knockback` | 冲击波（伤害 + 击退，不破坏方块） |
| `FREEZE` | `duration`, `duration-per-level`, `tick-damage` | 冰冻（细雪效果） |
| `SET_BLOCK` | `material`, `chance`, `radius` | 放置方块（严格限流，默认受保护区域限制） |
| `BREAK_BLOCK` | `radius`, `drops: true/false`, `limit` | 破坏方块（默认不掉落且限流） |
| `VEIN_MINE` | `radius`, `limit`, `drops: true/false`, `same-type: true` | 连锁挖矿 |
| `TREE_FELL` | `limit` | 连锁砍树（只砍原木/树叶，不回弹） |
| `SHEAR_AREA` | `radius` | 范围剪羊毛 |
| `AUTO_REPLANT` | 无 | 收获后自动补种 |
| `AUTO_SMELT` | `multiplier` | 自动熔炼掉落物 |
| `DURABILITY` | `base`(正=修复/负=损耗), `per-level` | 修改物品耐久 |
| `SOULS` | `base`, `per-level` | 增减物品灵魂计数 |
| `CANCEL_EVENT` | 无 | 取消本次事件（如取消死亡、取消掉落） |
| `SURVIVE` | `health`(保留心数), `invulnerable`(秒) | 致命伤时保留生命 |
| `CONSUME_HELD` | `amount` | 消耗手持物品数量 |
| `COMMAND` | `value`(可含 `{player}` `{target}` `{level}`), `as: CONSOLE/PLAYER` | 执行命令（危险，默认限权） |
| `MESSAGE` | `value`, `who: SELF/TARGET/NEARBY` | 发送消息 |
| `TITLE` | `title`, `subtitle`, `fade-in`, `stay`, `fade-out` | 发送标题 |
| `ACTIONBAR` | `value` | 发送动作栏 |
| `PARTICLE` | `particle`, `count`, `spread`, `speed`, `who: SELF/TARGET/BLOCK/LOCATION` | 粒子 |
| `SOUND` | `sound`, `volume`, `pitch`, `who` | 音效 |
| `ADD_XP` | `base`, `per-level`, `max` | 直接给予经验点（`ON_KILL`/`ON_FISH`/`ON_LOOT` 等） |
| `MULTIPLY_XP` | `base`, `per-level`, `max`（倍率，1 = 不变） | 经验倍率，配合 `ON_XP_GAIN` 使用 |
| `SOULBOUND` | 无字段 | 该物品死亡时不掉落（`ON_DEATH` 触发） |
| `ATTACK_SPEED` | `max-cooldown`（默认 1.1，秒） | 重置攻击冷却，用于「攻速」类效果 |
| `POTION_SELF` | 等同 `POTION` 且 `who: SELF` | 兼容写法 |
| `XP_MULTIPLY` | 等同 `MULTIPLY_XP` | 兼容写法 |
| `RUN_SCRIPT` | `script` | 预留：调用 JS 脚本（默认关闭，见 config.yml） |

> 未知 `type` 会在加载时告警并忽略**该条 action**，其余 action 仍生效。

---

## 6. 完整示例

```yaml
enchants:
  thunder_edge:
    display: "&e雷刃"
    tier: LEGENDARY
    max-level: 3
    types: [SWORD]
    description:
      - "&7命中时有 &f{value}%% &7几率召唤闪电"
      - "&7并附加 &f麻痹 &7效果"
    conflicts: [eternal_blade]
    triggers:
      ON_HIT:
        cooldown: 4.0
        conditions:
          - type: CHANCE
            value: 8
            per-level: 4
          - type: Y
            min: 0
            max: 320
        actions:
          - type: LIGHTNING
            base: 1.0
            damage: false
          - type: POTION
            who: TARGET
            potion: SLOWNESS
            duration: 30
            duration-per-level: 15
            amplifier: 1
          - type: PARTICLE
            particle: ELECTRIC_SPARK
            count: 20
            spread: 0.6
            who: TARGET
          - type: SOUND
            sound: ENTITY_LIGHTNING_BOLT_THUNDER
            volume: 0.6
            pitch: 1.4
```

```yaml
# custom.yml —— 玩家自定义示例：每级 25% 概率双倍掉落 + 每级 +1 幸运
enchants:
  my_lucky_star:
    display: "&#FFD700幸运星"
    tier: EPIC
    max-level: 4
    types: [PICKAXE, SHOVEL, FISHING_ROD]
    description:
      - "&7挖掘/钓鱼时有 &f{value}%% &7几率额外掉落"
    triggers:
      ON_BLOCK_BREAK:
        chance: 25
        chance-per-level: 10
        conditions:
          - type: BLOCK
            value: ["*_ORE"]
        actions:
          - type: MULTIPLY_DROP
            base: 2
            per-level: 0.5
            max: 4
      ON_FISH:
        chance: 20
        chance-per-level: 10
        actions:
          - type: MULTIPLY_DROP
            base: 2
            per-level: 0.5
```
