# 修复记录（独立评审 → 修复）

本文件记录一次独立红队评审（只读审计：契约一致性、Schema↔代码一致性、Bukkit API 正确性、空指针与边界、安全性）
发现的问题，以及本次修复的**代码落点**与**验证方式**。

评审结论：**129/129 条附魔的 ID / 等级 / 适用类型与契约一致，0 跨文件重复；无编译或加载级故障。**
所有问题均为运行期 / 逻辑 / 配置静默失效。

---

## 阻断级（已修）

| # | 问题 | 影响 | 修复落点 |
|---|---|---|---|
| 1 | `ctx.drops` 从不回写 → `MULTIPLY_DROP` / `CONVERT_DROP` / `AUTO_SMELT` 全是死代码 | **21 条**附魔（占 129 条 16%）、32 处动作无效果：全部 8 条钓鱼附魔、lucky_miner、vein_apocalypse、auto_smelt、smelter_master、compressor、farmer_gift、wool_shear、fortunes_favor、midas_touch、scavenger、xp_boost、swift_learner | `TriggerListener#writeBackDrops`：按序号回写 `event.getItems()` 的 `ItemStack`、删除多余实体、为新增条目在方块位置补生成掉落；`onFish` 对渔获实体做同样回写 |
| 2 | `DAMAGE_REDUCE` 符号反了：攻防共用同一个 `EntityDamageEvent` 与同一个 `EventMods` | 防御类附魔把自己的**攻击伤害放大**，受到伤害没减；`bulwark` / `undying_will` / `arcane_barrier` / `second_wind` 全部反向 | 新增 `applyOffense()` / `applyDefense()` 两条独立通道；减伤按附魔 ID 分桶 `dmg.reduce.<id>` 乘算叠加；`heal.attack` / `heal.defend` 分离 |
| 3 | `DODGE` 用早退 `return 0` | 同事件内的回血与 `SURVIVE` 被一起跳过 | 闪避改为只把伤害置 0 并返回，后续结算照常执行 |
| 4 | `ARMOR_DAMAGE` 直接 `getEquipment().getArmorContents()` | 1.20.4 起对无装备槽实体 `getEquipment()` 返回 null → 每次命中 NPE（`shatter`） | `ActionExec#armorDamage` 判空早退 |
| 5 | `TARGET_HEALTH value:1.0 mode:ABOVE` 用严格 `>` | 比例上限就是 1.0，条件恒假 → `first_strike` 永久失效 | `Conditions#compareRatio` 引入 `1e-6` epsilon |
| 6 | 三个触发器有枚举、有配置，但从未注册监听 | 6 条附魔零反应：`ON_FALL_DAMAGE`（acrobat / aero_armor / phoenix_wing）、`ON_SHIELD_BLOCK`（shield_wall / energy_shield）、`ON_BOW_CHARGE`（skybreaker_shot） | `onDamage` 内新增摔落伤害通道与举盾格挡通道；`EntityShootBowEvent` 在 force ≥ 0.9 时补发 `ON_BOW_CHARGE` |
| 7 | `ON_ENCHANT` 错绑在 `PrepareGrindstoneEvent` | 附魔台从未接入，磨刀石被当成附魔台 | 新增 `onEnchantItem`（`EnchantItemEvent`）承载 `ON_ENCHANT`；磨刀石改走独立分支并受 `grindstone.strip-enabled` 控制 |
| 8 | `EXPLOSION` 忽略 `base`/`per-level`/`max`；自伤条件取反 | 爆炸强度与配置脱钩；受害者永不受伤 | `explosion()` 改为读 `scaled()`；自伤改为 `self-damage`（默认 true） |
| 9 | `LIGHTNING` 读未文档化的 `chance` | `thunder_edge` 的 `base: 1.0` 概率配置失效（恒 100%） | 优先读 `base`，无 `base` 时回退 `chance` |
| 10 | 1.20.5 改名的药水/粒子在 1.20.4 静默失效 | `HASTE` 4 处 + `SLOWNESS` 7 处失效；`Particle.BLOCK`/`SMOKE` 等 13 项粒子失效 | `VersionSafe` 补 `HASTE↔FAST_DIGGING`、`SLOWNESS↔SLOW`；`ParticleAliases` 双向映射并修好被覆盖成自环的 `BLOCK` 链 |

## 中等（已修）

| # | 问题 | 修复落点 |
|---|---|---|
| 11 | `PlayerDeathEvent` 与 `EntityDeathEvent` 双触发 `ON_DEATH`（灵魂绑定耐久扣两次、消息发两遍） | `onDeath` 不再处理玩家，玩家统一由 `onPlayerDeath` 处理 |
| 12 | `ON_SHOOT` 被 `EntityShootBowEvent` 与 `ProjectileLaunchEvent` 双重触发（`multishot_plus`/`quick_draw` 每箭执行两次） | `onProjectileLaunch` 跳过玩家射出的箭，只接非弓类弹射物 |
| 13 | `SOULBOUND` 判据过宽：一件灵魂绑定保护全背包附魔物品 | 只移除**确实带 `soul_bound`** 的物品，并回报实际保护件数 |
| 14 | `SET_BLOCK` 忽略 `radius`、把 `chance: 100` 当 0~1 比例 | 支持 `radius`（限流 + 容器保护）；`chance` 兼容 0~1 与百分数两种写法 |
| 15 | `SOULS base: -1` 被 `Levels.plain` 钳成 0，消耗永不发生 | `souls()` 改为直接读 `base`/`per-level`，允许负值 |
| 16 | `COMMAND` 的 `{level}` 写死为 1 | 记录当前等级，`{level}`/`{value}` 正确替换 |
| 17 | `tier-weights` / `grindstone.strip-enabled` 等配置无人读取 | `EnchantLoader` 读 `tier-weights` 作为默认权重；`AdvancedEnchants` 把 `strip-enabled` 注入监听器；删除未实现且易误解的 `enchanting-table` / `anvil` 配置段并在注释里说明现状 |
| 18 | `advancedenchants.enchant.*` 权限节点三种命名不一致 | 统一为 `advancedenchants.enchant.<ID>`，在 `plugin.yml` 与 `docs/SCHEMA.md` 注明 |
| 19 | `ENCHANT_ROSTER` 类型统计表 8 个数与 yml 实测不符 | 按 `YamlVerifier` 实测值重写，并加复现命令 |
| 20 | `plugin.yml` 写「126+ 条」与 129 冲突 | 改为 129 |

## 验证方式

```bash
# 编译 + 打包（离线可用）
build-no-maven.cmd

# 配置与契约校验：129 条唯一 ID / 160 触发块 / 0 错误 0 警告
java -cp "target/classes;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.YamlVerifier src/main/resources/enchants

# 枚举名审计（含运行时别名回退统计）
java -cp "target/classes;<paper-api.jar>;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.EnumAudit src/main/resources/enchants
```

最近一次结果：编译 0 错误 / 36 个 class；`YamlVerifier` 结果 `PASS`；
`EnumAudit`：PARTICLE 全部可用（13 项经运行时别名回退）、SOUND 1539 项全命中、ENTITY / MATERIAL 全命中。

## 仍未验证 / 已知限制

- **未在真实服务端启动过**：本机无 Paper 服务端与网络，只做到「编译 + 静态校验 + 字节码加载」。
  首次上测试服请检查控制台是否有 `未知药水效果` / `不支持 Particle` 告警，以及 `/ae list` 是否显示 129 条。
- 附魔台随机池、铁砧叠加的额外经验消耗**尚未实现**（见 `config.yml` 注释）。
- 条件之间只有 AND，无 OR；无领地/区域插件联动。
- `RUN_SCRIPT` 仅预留接口，默认关闭。
- Folia 未适配（`folia-supported: false`）。
