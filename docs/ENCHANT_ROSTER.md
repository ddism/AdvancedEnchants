# AdvancedEnchants 附魔总表（129 条 · 契约文件）

> 本文件是**唯一事实来源**。`resources/enchants/*.yml` 必须与本表逐一对应：ID、等级、适用类型三者不可偏离；效果描述允许在实现细节上更丰富，但不得改变主题。
> 类型代号：`SWORD`=剑 · `AXE`=斧 · `TRIDENT`=三叉戟 · `BOW`=弓 · `CROSSBOW`=弩 · `ARMOR`=盔甲（头盔/胸甲/护腿/靴子）· `PICKAXE`=镐 · `SHOVEL`=锹 · `HOE`=锄 · `SHEARS`=剪刀 · `FISHING_ROD`=钓鱼竿 · `ELYTRA`=鞘翅 · `SHIELD`=盾牌 · `ALL`=任意（由 `types` 显式列全）

等级：`COMMON 普通` · `UNCOMMON 优秀` · `RARE 稀有` · `EPIC 史诗` · `LEGENDARY 传说` · `MYTHIC 神话`

> 统计口径说明：**条数以本表逐行数据为准**。v1.1 修正了 v1.0 统计表的错算（EPIC 实为 19、RARE 实为 29），并补齐 3 条新附魔（127~129 行后编号见文末「v1.1 增补」）。

---

## 1. 剑 SWORD（24）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 1 | `lifesteal` | 吸血 | COMMON | SWORD, AXE | 造成伤害的 5%/级 回复自身生命 |
| 2 | `keen_edge` | 锋利之刃 | COMMON | SWORD, AXE | 每级 +5% 伤害 |
| 3 | `butcher` | 屠夫 | COMMON | SWORD | 对被动生物伤害 +12%/级 |
| 4 | `scavenger` | 拾荒者 | COMMON | SWORD | 击杀掉落额外经验与随机杂物 |
| 5 | `duelist` | 决斗者 | COMMON | SWORD | 1v1（附近无敌方玩家）时 +8%/级 伤害 |
| 6 | `hunter_mark` | 猎手印记 | COMMON | SWORD, BOW | 命中后目标被标记，受你伤害 +4%/级（5s） |
| 7 | `first_strike` | 先手 | UNCOMMON | SWORD | 目标满血时必定暴击，暴击伤害 ×1.3/级 |
| 8 | `combo_master` | 连击大师 | UNCOMMON | SWORD | 连续命中叠层，每层 +2.5%/级（上限 6 层） |
| 9 | `execute` | 处决 | UNCOMMON | SWORD, AXE | 目标生命低于 20% 时伤害 +25%/级 |
| 10 | `venom_blade` | 淬毒之刃 | UNCOMMON | SWORD | 命中附加中毒（等级随附魔等级） |
| 11 | `frostbite` | 霜噬 | UNCOMMON | SWORD | 命中附加缓慢与挖掘疲劳 |
| 12 | `blinding_strike` | 致盲打击 | UNCOMMON | SWORD | 命中附加失明 2s/级 |
| 13 | `mending_strike` | 汲取打击 | RARE | SWORD | 命中回复武器耐久 2/级 |
| 14 | `soul_harvest` | 灵魂收割 | RARE | SWORD | 击杀获得灵魂，每 5 灵魂 +1% 伤害 |
| 15 | `berserker` | 狂战士 | RARE | SWORD, AXE | 生命越低伤害越高（最多 +30%/级） |
| 16 | `cleave` | 横扫 | RARE | SWORD, AXE | 命中时对周围 2 格内其他敌人造成 40%/级 溅射 |
| 17 | `parry` | 招架 | RARE | SWORD | 受到攻击时 10%/级 概率减伤 60% 并反击 |
| 18 | `blade_dance` | 剑舞 | EPIC | SWORD | 命中后 3s 内攻速提升，最高 +20%/级 |
| 19 | `phantom_strike` | 幻影打击 | EPIC | SWORD | 概率打出无视护甲的真实伤害 |
| 20 | `hemorrhage` | 裂伤 | EPIC | SWORD | 目标持续流血，移动时流血加倍，不致死 |
| 21 | `thunder_edge` | 雷刃 | LEGENDARY | SWORD | 命中召唤闪电（有冷却），并附加麻痹 |
| 22 | `soul_eater` | 噬魂 | LEGENDARY | SWORD | 击杀永久 +3%/层 伤害（上限 25 层，死亡减半） |
| 23 | `vampiric_aura` | 血族光环 | LEGENDARY | SWORD | 周围敌人被汲取，治疗你与队友 |
| 24 | `eternal_blade` | 永恒之刃 | MYTHIC | SWORD | 斩杀生效时重置冷却并回满武器耐久 |

## 2. 斧 AXE（8）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 25 | `gigantism` | 巨力 | COMMON | AXE | 击退与击飞效果增强 |
| 26 | `lumberjack` | 伐木狂 | UNCOMMON | AXE, PICKAXE, SHOVEL | 破坏方块速度 +15%/级 |
| 27 | `shatter` | 碎甲 | RARE | AXE | 命中使目标护甲耐久受损并降低其减伤 1 级 |
| 28 | `crushing_blow` | 碎骨重击 | RARE | AXE | 无视目标部分护甲值的伤害加成 |
| 29 | `war_cry` | 战吼 | EPIC | AXE | 右键发出战吼，周围敌人虚弱、自身速度提升 |
| 30 | `butcher_cleave` | 屠戮 | EPIC | AXE | 溅射伤害提升且对生物额外加成 |
| 31 | `execute_axe` | 断头台 | LEGENDARY | AXE | 对低血量目标额外提升处决阈值至 35% |
| 32 | `storm_breaker` | 破军 | MYTHIC | AXE | 蓄力重击造成范围冲击波并击退，冷却较长 |

## 3. 三叉戟 TRIDENT（4）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 33 | `tidal_force` | 潮汐之力 | UNCOMMON | TRIDENT | 雨天伤害 +12%/级 |
| 34 | `riptide_master` | 激流掌控 | RARE | TRIDENT | 激流突进伤害提升且不消耗额外耐久 |
| 35 | `abyss_hunter` | 深渊猎手 | EPIC | TRIDENT | 对水中目标伤害 +30%/级 并附加缓慢 |
| 36 | `poseidon_wrath` | 海皇之怒 | LEGENDARY | TRIDENT | 命中引发范围水爆并击退，雨天强化 |

## 4. 弓 / 弩 BOW · CROSSBOW（16）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 37 | `power_shot` | 强力射击 | COMMON | BOW, CROSSBOW | 箭矢伤害 +8%/级 |
| 38 | `longshot` | 远射 | COMMON | BOW, CROSSBOW | 距离越远伤害越高（最多 +25%/级） |
| 39 | `hunter_instinct` | 猎手本能 | COMMON | BOW | 命中动物额外掉落 |
| 40 | `steady_aim` | 沉稳瞄准 | COMMON | BOW | 拉满弓时伤害与弹道更稳 |
| 41 | `multishot_plus` | 多重射击 | UNCOMMON | CROSSBOW | 额外射出箭矢 |
| 42 | `piercing_shot` | 穿透射击 | UNCOMMON | BOW, CROSSBOW | 箭矢可穿透多个目标 |
| 43 | `poison_arrow` | 毒箭 | UNCOMMON | BOW, CROSSBOW | 命中附加中毒 |
| 44 | `quick_draw` | 速射 | UNCOMMON | BOW | 拉弓速度提升，缩短蓄力时间 |
| 45 | `explosive_arrow` | 爆裂箭 | RARE | BOW, CROSSBOW | 命中产生小型爆炸（不破坏地形） |
| 46 | `grappling_arrow` | 钩索箭 | RARE | BOW, CROSSBOW | 命中方块将你拉向箭矢落点 |
| 47 | `ricochet` | 跳弹 | RARE | BOW, CROSSBOW | 箭矢击中实体后弹射到下一个目标 |
| 48 | `sniper` | 狙击手 | EPIC | BOW, CROSSBOW | 远距离命中造成暴击伤害提升 |
| 49 | `homing_arrow` | 追踪箭 | EPIC | BOW | 箭矢自动追踪最近的敌人 |
| 50 | `arrow_rain` | 箭雨 | EPIC | BOW | 命中后原地落下小型箭雨（有冷却） |
| 51 | `soul_arrow` | 灵魂箭 | LEGENDARY | BOW | 命中吸取目标生命并提供短暂抗性 |
| 52 | `skybreaker_shot` | 天穹之箭 | MYTHIC | BOW, CROSSBOW | 蓄力满弓射出贯穿光束，穿透并击飞路径目标 |

## 5. 盔甲 ARMOR（25）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 53 | `bulwark` | 壁垒 | COMMON | ARMOR | 受到伤害降低 3%/级 |
| 54 | `thorns_plus` | 荆棘强化 | COMMON | ARMOR | 反弹伤害提高，附加击退 |
| 55 | `health_boost` | 强健体魄 | COMMON | ARMOR | 最大生命提升（按件叠加） |
| 56 | `swift_step` | 疾风之靴 | COMMON | ARMOR | 移动速度提升 |
| 57 | `acrobat` | 杂技 | COMMON | ARMOR | 减少坠落伤害 |
| 58 | `last_stand` | 背水一战 | UNCOMMON | ARMOR | 低血量时获得抗性提升 |
| 59 | `fireproof` | 耐火 | UNCOMMON | ARMOR | 减少火焰伤害与燃烧时间 |
| 60 | `deep_diver` | 深潜者 | UNCOMMON | ARMOR | 水下呼吸与水下挖掘加速 |
| 61 | `regeneration_aura` | 再生光环 | UNCOMMON | ARMOR | 脱战持续回血 |
| 62 | `deflect` | 偏转 | UNCOMMON | ARMOR | 概率完全免疫投射物伤害 |
| 63 | `elemental_ward` | 元素护盾 | RARE | ARMOR | 降低魔法/药水/凋零类伤害 |
| 64 | `second_wind` | 复苏之风 | RARE | ARMOR | 致命伤害时留 1 血并获得短暂无敌（长冷却） |
| 65 | `guardian_angel` | 守护天使 | RARE | ARMOR | 队友在附近时获得额外减伤 |
| 66 | `shield_wall` | 盾墙 | RARE | ARMOR, SHIELD | 潜行时大幅减伤但移速下降 |
| 67 | `pain_suppression` | 痛觉抑制 | RARE | ARMOR | 受到伤害后短时间内提升移速 |
| 68 | `retribution` | 反伤甲 | EPIC | ARMOR | 受击后对攻击者造成真实反伤 |
| 69 | `juggernaut` | 势不可挡 | EPIC | ARMOR | 连击/受击叠层获得减伤与抗击退，脱战清空 |
| 70 | `phoenix_feather` | 不死鸟之羽 | EPIC | ARMOR | 死亡时以半血复活并燃烧周围敌人（每场战斗一次） |
| 71 | `undying_will` | 不屈意志 | EPIC | ARMOR | 血量越低减伤越高，濒死时获得抗性提升 |
| 72 | `divine_shield` | 神圣壁垒 | LEGENDARY | ARMOR | 周期性生成吸收伤害的护盾值 |
| 73 | `arcane_resistance` | 秘法抗性 | LEGENDARY | ARMOR | 高额减免魔法与爆炸伤害，反弹部分法伤 |
| 74 | `life_link` | 生命链接 | LEGENDARY | ARMOR | 将部分伤害分摊给附近队友并提升全队回血 |
| 75 | `astral_body` | 星界之躯 | MYTHIC | ARMOR | 概率使伤害完全无效并短暂获得抗性提升 |
| 76 | `eternal_guardian` | 永恒守护 | MYTHIC | ARMOR | 全队共享减伤光环，自身濒死时全队获得护盾 |
| 77 | `adaptive_armor` | 自适应装甲 | MYTHIC | ARMOR | 受到某类伤害后永久（本场）提升该类抗性 |

## 6. 工具 PICKAXE · SHOVEL · HOE · SHEARS（17）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 78 | `vein_miner` | 连锁采矿 | COMMON | PICKAXE | 连锁破坏同类矿石（范围内限流） |
| 79 | `auto_smelt` | 自动熔炼 | COMMON | PICKAXE, SHOVEL | 掉落物自动熔炼 |
| 80 | `lucky_miner` | 幸运矿工 | COMMON | PICKAXE | 提升矿物掉落数量 |
| 81 | `lumberjack_tool` | 伐木工 | COMMON | AXE, PICKAXE, SHOVEL, HOE | 破坏方块速度提升 |
| 82 | `tree_feller` | 伐木连锁 | UNCOMMON | AXE | 砍倒整棵树（限流且不损坏地形） |
| 83 | `harvester` | 丰收 | UNCOMMON | HOE | 收获作物时自动补种 |
| 84 | `farmer_gift` | 农夫的馈赠 | UNCOMMON | HOE | 额外掉落作物并提升生长速度 |
| 85 | `excavator` | 挖掘者 | UNCOMMON | SHOVEL | 3×3 范围挖掘 |
| 86 | `soil_purifier` | 净壤 | UNCOMMON | SHOVEL | 将沙土转化为可耕地并清除菌丝/雪 |
| 87 | `wool_shear` | 羊毛剪 | UNCOMMON | SHEARS | 剪羊毛产量翻倍并自动收集 |
| 88 | `block_breaker` | 碎岩 | RARE | PICKAXE | 提升对高硬度方块（黑曜石等）的挖掘速度 |
| 89 | `fortunes_favor` | 幸运女神 | RARE | PICKAXE, SHOVEL, HOE | 随机获得额外资源（含珍稀物） |
| 90 | `smelter_master` | 熔炉大师 | RARE | PICKAXE, SHOVEL | 自动熔炼产出翻倍 |
| 91 | `compressor` | 压缩者 | RARE | PICKAXE, SHOVEL, HOE | 掉落物自动压缩为块 |
| 92 | `gravity_digger` | 重力挖掘 | EPIC | PICKAXE | 范围挖掘并使上方沙砾坠落 |
| 93 | `vein_apocalypse` | 矿脉浩劫 | EPIC | PICKAXE | 大范围连锁采矿（严格限流，破坏方块的 50% 掉落） |
| 94 | `terraforming` | 地形塑造 | MYTHIC | PICKAXE, SHOVEL, HOE | 右键一键平整/填充选区（可撤销，限流） |

## 7. 钓鱼竿 FISHING_ROD（10）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 95 | `anglers_blessing` | 海神赐福 | COMMON | FISHING_ROD | 提高钓到鱼的几率 |
| 96 | `big_catch` | 渔获丰收 | COMMON | FISHING_ROD | 提升渔获数量 |
| 97 | `treasure_hunter` | 宝藏猎手 | UNCOMMON | FISHING_ROD | 提升钓到宝物的几率 |
| 98 | `golden_catch` | 黄金渔获 | UNCOMMON | FISHING_ROD | 有概率钓上金锭/宝石 |
| 99 | `auto_reel` | 自动收竿 | UNCOMMON | FISHING_ROD | 咬钩后自动收竿并抛出下一杆 |
| 100 | `junk_to_treasure` | 变废为宝 | RARE | FISHING_ROD | 钓到垃圾时转化为可用资源 |
| 101 | `xp_fisher` | 垂钓达人 | RARE | FISHING_ROD | 钓鱼获得的经验大幅提升（MULTIPLY_XP） |
| 102 | `master_angler` | 渔王 | EPIC | FISHING_ROD | 全部渔获品质提升并额外掉落 |
| 103 | `kraken_call` | 海怪呼唤 | LEGENDARY | FISHING_ROD | 低概率钓出敌对生物并给予高额奖励 |
| 104 | `void_fishing` | 虚空垂钓 | MYTHIC | FISHING_ROD | 可钓出末地/下界专属稀有物与附魔书 |

## 8. 鞘翅 / 特殊 ELYTRA · SHIELD（8）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 105 | `glide_boost` | 滑翔加速 | UNCOMMON | ELYTRA | 滑翔时水平速度提升 |
| 106 | `rocket_jump` | 火箭助推 | RARE | ELYTRA | 滑翔时右键获得额外推力（消耗耐久，有冷却） |
| 107 | `aero_armor` | 气动护甲 | RARE | ELYTRA | 滑翔时获得减伤，撞击不受伤 |
| 108 | `cloud_walker` | 踏云者 | EPIC | ELYTRA | 滑翔时可短暂悬浮与二段拉升 |
| 109 | `sky_terror` | 空域霸主 | EPIC | ELYTRA | 空中攻击伤害提升并造成击退 |
| 110 | `phoenix_wing` | 不死鸟之翼 | LEGENDARY | ELYTRA | 高空坠落时自动滑翔并免疫落地伤害 |
| 111 | `void_sovereign` | 虚空主宰 | MYTHIC | ELYTRA | 滑翔穿过敌人造成伤害并短暂免疫伤害 |
| 112 | `energy_shield` | 能量盾 | RARE | SHIELD, ARMOR | 举盾时按比例吸收并反弹部分伤害 |

## 9. 通用 / 特殊 ALL（14）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 113 | `xp_boost` | 经验加成 | COMMON | ALL | 击杀与挖掘获得额外经验（MULTIPLY_XP） |
| 114 | `unbreakable_will` | 坚不可摧 | UNCOMMON | ALL | 概率不消耗耐久 |
| 115 | `soul_collector` | 灵魂收集 | UNCOMMON | ALL | 击杀累积灵魂，可用于强化其他附魔 |
| 116 | `prospector` | 探矿者 | RARE | ALL | 挖掘时提示附近矿脉位置 |
| 117 | `world_breaker` | 世界破坏者 | MYTHIC | ALL | 极低概率触发范围破坏（可配置严格限流与保护） |
| 118 | `soul_bound` | 灵魂绑定 | RARE | ALL | 死亡时物品不掉落（SOULBOUND 动作） |
| 119 | `glass_cannon` | 玻璃大炮 | EPIC | ALL | 伤害大幅提升但自身受到的伤害增加（DAMAGE_ADD target: SELF） |
| 120 | `jack_of_all_trades` | 万事通 | RARE | ALL | 全属性小幅提升 |
| 121 | `vampiric_touch` | 吸血之触 | UNCOMMON | ALL | 任意伤害有概率汲取生命 |
| 122 | `midas_touch` | 点石成金 | LEGENDARY | ALL | 掉落物有概率转化为金锭/经验 |
| 123 | `disease_curse` | 疾病诅咒 | COMMON | ALL | 诅咒：持有者持续受到轻微伤害 |
| 124 | `curse_of_brittleness` | 脆弱诅咒 | UNCOMMON | ALL | 诅咒：耐久消耗翻倍 |
| 125 | `curse_of_binding` | 束缚诅咒 | RARE | ALL | 诅咒：无法主动丢弃该物品 |
| 126 | `swift_learner` | 快速学习 | COMMON | ALL | 经验获取提升，技能升级更快（MULTIPLY_XP） |

---

## v1.1 增补（补齐统计口径，3 条）

| # | ID | 中文名 | 等级 | 适用类型 | 效果要点 |
|---|---|---|---|---|---|
| 127 | `leeching_strike` | 汲取打击 | RARE | SWORD, AXE | 命中真实伤害并按比例转化为自身治疗（与吸血叠加） |
| 128 | `arcane_barrier` | 秘法屏障 | EPIC | ARMOR | 受到伤害时消耗灵魂值吸收伤害（SOULS 条件 + DAMAGE_REDUCE） |
| 129 | `soul_siphon` | 灵魂虹吸 | EPIC | SWORD, ALL | 击杀时窃取目标灵魂并转为自身护盾/回复 |

---

## 统计（v1.2 · 与 `src/main/resources/enchants/*.yml` 实测一致）

| 文件 | 条数 | 等级 |
|---|---|---|
| `10-common.yml` | 25 | COMMON |
| `20-uncommon.yml` | 31 | UNCOMMON |
| `30-rare.yml` | 30 | RARE |
| `40-epic.yml` | 21 | EPIC |
| `50-legendary.yml` | 12 | LEGENDARY |
| `60-mythic.yml` | 10 | MYTHIC |
| **合计** | **129** | **C25 + U31 + R30 + E21 + L12 + M10 = 129** |

按类型覆盖（同一条附魔可属多类，故分类和大于 129；下列数字由 `YamlVerifier` 直接扫描 yml 得出，非人工统计）：

| 类型 | 条数 | 代表附魔 |
|---|---|---|
| 剑 SWORD | 26 | lifesteal / execute / thunder_edge / eternal_blade |
| 斧 AXE | 16 | gigantism / shatter / war_cry / storm_breaker |
| 三叉戟 TRIDENT | 4 | tidal_force / riptide_master / abyss_hunter / poseidon_wrath |
| 弓 BOW | 16 | power_shot / piercing_shot / homing_arrow / skybreaker_shot |
| 弩 CROSSBOW | 10 | multishot_plus / explosive_arrow / ricochet / sniper |
| 盔甲 ARMOR（含 4 部位与海龟壳） | 27 | bulwark / second_wind / divine_shield / astral_body |
| 盾牌 SHIELD | 2 | shield_wall / energy_shield |
| 镐 PICKAXE | 12 | vein_miner / block_breaker / gravity_digger / terraforming |
| 锹 SHOVEL | 9 | excavator / soil_purifier / smelter_master / compressor |
| 锄 HOE | 6 | harvester / farmer_gift / fortunes_favor / terraforming |
| 剪刀 SHEARS | 1 | wool_shear |
| 钓鱼竿 FISHING_ROD | 10 | anglers_blessing / treasure_hunter / kraken_call / void_fishing |
| 鞘翅 ELYTRA | 7 | glide_boost / rocket_jump / cloud_walker / void_sovereign |
| 任意 ALL | 15 | xp_boost / soul_bound / midas_touch / world_breaker |

> 口径说明：`ALL` 是独立类型，**不**并入其它类型计数；`ARMOR` 计入头盔 / 胸甲 / 护腿 / 靴子 / 海龟壳。
> 复现命令：`java -cp "target/classes;.verify-libs/snakeyaml-2.2.jar" com.advancedenchants.verify.YamlVerifier src/main/resources/enchants`

覆盖类型：剑、斧、三叉戟、弓、弩、盔甲（4 部位）、盾牌、镐、锹、锄、剪刀、钓鱼竿、鞘翅、任意物品 —— **全部工具与武器类型均有专属附魔**。
