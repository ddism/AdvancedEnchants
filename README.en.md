[中文](README.md) | **English**

# AdvancedEnchants

**A config-driven custom enchantment engine for Paper** — ships **129 tiered enchantments** covering swords / axes / tridents / bows / crossbows / armor / shields / pickaxes / shovels / hoes / shears / fishing rods / elytra / any item. **Every enchantment — including the ones you design yourself — is defined in YAML**: edit the config, run `/ae reload`, done. No Java, no recompiling.

> In-game text (enchantment names, `/ae` help, tier names) is currently **Chinese**. See [Localization](#localization).

```
┌──────────────────────────────┐
│  YAML config (enchants/*.yml)│  ← server owners design enchantments here
└──────────────┬───────────────┘
               │ EnchantLoader: parse + validate
┌──────────────▼───────────────┐
│  EnchantRegistry             │  ← definitions + server registration result
└──────────────┬───────────────┘
               │ events
┌──────────────▼───────────────┐
│  TriggerListener (26 triggers)│  ← translates Bukkit events into a context
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  Engine: conditions + cooldown/chance │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│  ActionExec (44 actions)     │  ← damage / status / world / item / feedback
└──────────────────────────────┘
```

---

## 1. Quick start

```bash
# 1) Build (defaults to paper-api 1.20.4; the artifact runs on 1.20.4 ~ 1.21.x)
mvn clean package
# Already on a target version and online:
mvn -Ppaper-1.21.4 clean package

# No Maven / fully offline: this script uses javac + the JDK jar tool and
# locates local dependencies automatically (~3 seconds).
build-no-maven.cmd

# 2) Deploy
Drop dist/AdvancedEnchants-1.0.0.jar into your server's plugins/ directory and restart.

# 3) Generated on first start
plugins/AdvancedEnchants/
├── config.yml
└── enchants/
    ├── 10-common.yml   (25)   ├── 40-epic.yml      (21)
    ├── 20-uncommon.yml (31)   ├── 50-legendary.yml (12)
    ├── 30-rare.yml     (30)   ├── 60-mythic.yml    (10)
    └── custom.yml      ← put your own enchantments here (never overwritten)
```

## 2. Commands and permissions

| Command | Description | Permission (default) |
|---|---|---|
| `/ae` | Help and status | `advancedenchants.use` (true) |
| `/ae list [tier]` | List enchantments (filter by COMMON~MYTHIC) | `advancedenchants.admin` (op) |
| `/ae info <ID>` | Show one enchantment (triggers / chance / cooldown / action count) | `advancedenchants.admin` |
| `/ae give <ID> [level] [player]` | Give an enchanted book | `advancedenchants.admin` |
| `/ae enchant <ID> [level]` | Enchant the held item (validates applicable types) | `advancedenchants.use` |
| `/ae clear` | Strip custom enchantments from the held item | `advancedenchants.use` |
| `/ae inspect` | Show the held item's enchantment data and soul count | `advancedenchants.use` |
| `/ae reload` | Reload config.yml and every enchantment file | `advancedenchants.admin` |
| `/ae debug` | Toggle trigger debug logging (console) | `advancedenchants.admin` |

Aliases: `/ae`, `/aench`, `/advench`.

## 3. Enchantment system

### 3.1 Six tiers

| Tier | Color | Default weight | Count |
|---|---|---|---|
| COMMON | gray | 100 | 25 |
| UNCOMMON | green | 55 | 31 |
| RARE | blue | 26 | 30 |
| EPIC | purple | 10 | 21 |
| LEGENDARY | gold | 4 | 12 |
| MYTHIC | red | 1 | 10 |

The full list (ID, name, tier, applicable types, effect) is in
**[docs/ENCHANT_ROSTER.md](docs/ENCHANT_ROSTER.md)** (Chinese).

### 3.2 Type coverage

Sword 26 · Armor 27 · Axe 16 · Bow 16 · Any 15 · Pickaxe 12 · Crossbow 10 · Fishing rod 10 · Shovel 9 · Elytra 7 · Hoe 6 · Trident 4 · Shield 2 · Shears 1
(One enchantment may belong to several types, so the sum exceeds 129.)

### 3.3 Capabilities

- **26 triggers**: `ON_HIT` `ON_KILL` `ON_DAMAGED` `ON_DEATH` `ON_BLOCK_BREAK` `ON_BLOCK_PLACE` `ON_MINE_TICK` `ON_SHOOT` `ON_PROJECTILE_HIT` `ON_FISH` `ON_JUMP` `ON_FALL_DAMAGE` `ON_MOVE` `ON_SNEAK` `ON_BOW_CHARGE` `ON_SHIELD_BLOCK` `ON_INTERACT` `ON_ANVIL_COMBINE` `ON_ENCHANT` `ON_EQUIP` `ON_CONSUME` `ON_SWAP_HAND` `PASSIVE` `ON_LOOT` `ON_XP_GAIN` `ON_DISABLE`
- **26 conditions**: chance / health ratio / target health / sneaking / sprinting / on ground / in water / raining / burning / time / Y range / world / biome / block / target type / potion / permission / souls / existing enchantment / day / night / difficulty / projectile distance / bow force / nearby allies …
- **44 actions**: multiply damage / flat damage / true damage / percent-health damage / execute / lifesteal / heal / damage reduction / dodge / damage cap / reflect / vulnerability mark / armor pierce / drop multiply and convert / potions / ignite / knockback / launch / pull / teleport / summon / lightning / explosion / potion cloud / spawn projectile / arrow rain / shockwave / freeze / set and break blocks / vein mine / tree fell / area shearing / auto replant / auto smelt / durability / souls / soulbound / cancel event / survive lethal / attack cooldown / consume held / command / message / title / action bar / particle / sound / XP multiplier and grant …
- **Level formula**: `(base + per-level × (level-1)) × mode`, where mode is `LINEAR / QUADRATIC / EXPONENTIAL / DIMINISHING`, clamped by `max`.

## 4. Designing your own enchantment (3 minutes)

Edit `plugins/AdvancedEnchants/enchants/custom.yml`, then run `/ae reload`:

```yaml
enchants:
  my_lucky_star:
    display: "&#FFD700Lucky Star"     # supports & codes and &#RRGGBB
    tier: EPIC                        # COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC
    max-level: 4
    types: [PICKAXE, SHOVEL]          # applicable item types
    description:
      - "&7{value}%% chance for extra drops while mining"   # {value} = first action's value at this level
    triggers:
      ON_BLOCK_BREAK:
        chance: 25                    # trigger chance (%)
        chance-per-level: 10
        conditions:                   # all must pass (AND)
          - type: BLOCK
            value: ["*_ORE"]
        actions:                      # executed in order
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

Multiple parameter sets on the same trigger use list form:

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

The complete field reference (every legal trigger / condition / action and its fields) is in
**[docs/SCHEMA.md](docs/SCHEMA.md)** (Chinese; field names are English).

## 5. Obtaining enchantments in game

| Route | Notes |
|---|---|
| Enchanted book | `/ae give <ID> [level]`; combine on an anvil to level up (capped by `max-level`) |
| Anvil | Left slot = item, right slot = book with the same enchantment |
| Grindstone | Strips custom enchantments (`grindstone.strip-enabled` in config.yml) |
| Command | `/ae enchant <ID> [level]` on the held item |
| Storage | Data lives in item NBT (PersistentDataContainer) — survives restarts, transfers and shulker boxes |

> On 1.21.x the plugin registers enchantments through `Registry#register`, so the item tooltip and glint render
> normally. On older versions, or if registration fails, it degrades to **lore-only display**; the effects are unaffected.

## 6. Configuration

Key entries in `config.yml`:

| Key | Default | Description |
|---|---|---|
| `max-enchants-per-item` | 12 | Max custom enchantments per item |
| `passive-interval-ticks` | 20 | `PASSIVE` trigger interval |
| `allow-terrain-changes` | true | Allow terrain-modifying effects (vein mine, explosive arrow…) |
| `allow-commands` | **false** | Allow the `COMMAND` action (kept off to prevent privilege escalation) |
| `protect-block-containers` | true | Protect containers from area-breaking effects |
| `tier-weights` | see file | Default random weights per tier (overridden by `rarity-weight` in an entry) |
| `grindstone.strip-enabled` | true | Whether a grindstone may strip custom enchantments |
| `debug` | false | Trigger logging |

## 7. Development and self-checks

```bash
# Compile
mvn clean package
# Offline / no Maven (javac + JDK jar, locates local deps automatically):
build-no-maven.cmd

# Static self-check #1 — parses every YAML, validates fields, whitelists and safety rules
java -cp "target/classes;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.YamlVerifier src/main/resources/enchants

# Static self-check #2 — audits PARTICLE / SOUND / ENTITY / MATERIAL names against the target API
# (requires paper-api on the classpath)
java -cp "target/classes;<paper-api.jar>;.verify-libs/snakeyaml-2.2.jar" \
     com.advancedenchants.verify.EnumAudit src/main/resources/enchants
```

Latest results:

| Check | Result |
|---|---|
| `javac --release 21` (paper-api 1.20.4) | ✅ 0 errors, 36 classes |
| `build-no-maven.cmd` end to end | ✅ compile → copy resources → package, produces `dist/AdvancedEnchants-1.0.0.jar` (57 entries) |
| `jar tf` structure | ✅ every entry uses forward slashes; contains `plugin.yml`, `config.yml` and 7 `enchants/` files |
| `YamlVerifier` | ✅ PASS: 129 unique IDs / 160 trigger blocks / 0 errors 0 warnings |
| `EnumAudit` | ✅ PARTICLE all usable (13 via runtime aliases), SOUND 1539 constants all matched, ENTITY / MATERIAL matched |
| Independent red-team review | ✅ contract 129/129 consistent; all findings fixed — see [docs/FIXES.md](docs/FIXES.md) (Chinese) |

> ⚠️ **Never started on a real server yet** (the build machine has no Paper server).
> On your first test-server run, check the console for `未知药水效果` (unknown potion effect) /
> `不支持 Particle` (unsupported particle) warnings, and confirm `/ae list` shows 129 entries.

### Project layout

```
AdvancedEnchants/
├── pom.xml
├── build-no-maven.cmd       # offline build script (javac + jar)
├── README.md                # 中文
├── README.en.md             # English (this file)
├── dist/
│   └── AdvancedEnchants-1.0.0.jar   # ready to drop into plugins/
├── docs/
│   ├── SCHEMA.md            # config reference (Chinese; English field names)
│   ├── ENCHANT_ROSTER.md    # all 129 enchantments (Chinese)
│   └── FIXES.md             # review findings and fixes (Chinese)
└── src/main/
    ├── java/com/advancedenchants/
    │   ├── AdvancedEnchants.java      # main class: lifecycle, registration, tasks
    │   ├── command/EnchantCommands.java
    │   ├── core/    EnchantLoader · EnchantRegistry · Engine · ActionExec
    │   │            Conditions · TriggerCtx · EventMods
    │   ├── listener/TriggerListener.java   # 26 Bukkit events → triggers
    │   ├── model/   EnchantDef · TriggerDef · CondDef · ActionDef · Tier
    │   │            ApplyType · MaterialTypes · Levels · Trigger
    │   ├── storage/ ItemTags (PDC/NBT) · LoreRenderer (lore rendering)
    │   ├── util/    VersionSafe (cross-version adapters) · ParticleAliases · Messages
    │   └── verify/  YamlVerifier · EnumAudit (static self-check tools)
    └── resources/
        ├── plugin.yml · config.yml
        └── enchants/*.yml
```

## 8. Cross-version support and known limitations

**Version strategy**: compiled against paper-api **1.20.4**, runs on **1.20.4 ~ 1.21.x**. 1.20.4's API is a subset of
1.21.x, so compiling against the older version is safe in that direction. Where 1.21-only APIs are needed
(`Registry#register` for custom enchantment registration, `setBasePotionType`, Adventure message overloads),
the code goes through `util/VersionSafe` and reflection, degrading gracefully instead of crashing.

**Known limitations** (configs use close approximations; normal gameplay is unaffected):

| Limitation | Current state |
|---|---|
| Conditions are AND-only, no OR | Split into several triggers for mutually exclusive branches (e.g. `ON_HIT` + `ON_PROJECTILE_HIT`) |
| Some effects have no dedicated action | e.g. bleeding DoT uses `POTION POISON`, area splash uses `SHOCKWAVE`, block compression uses `CONVERT_DROP` |
| No land-claim plugin integration | Destructive actions have built-in container protection, `limit` throttling and the `allow-terrain-changes` master switch; pair with a claim plugin |
| Enchanting-table random pool / anvil XP cost | **Not implemented** (see the notes in `config.yml`) |
| `RUN_SCRIPT` | Reserved hook only, disabled by default |
| Potion `duration` unit | **ticks** (20 = 1 second); `IGNITE`'s `base` is in **seconds** |
| Particle/sound names | Cross-version renames are mapped automatically by `ParticleAliases`; a bad name logs a warning and skips that effect |
| Folia | Not supported (`folia-supported: false`) — the engine relies on the main-thread event model |

## 9. Localization

All player-facing text is currently **Chinese**:

- `display` and `description` of every enchantment in `enchants/*.yml`
- `/ae` command output and tab completion
- Tier names in `/ae list` (普通 / 优秀 / 稀有 / 史诗 / 传说 / 神话)
- In-game messages (e.g. the soulbound notice)

Field names, IDs, enum values and log prefixes are English. To localize:

1. **Enchantment display names** — edit `display` / `description` in the YAML files directly (no rebuild needed).
2. **Command output and tier names** — currently hardcoded in `command/EnchantCommands.java`, `model/Tier.java`
   and `util/Messages.java`. A proper `lang/zh_CN.yml` + `lang/en_US.yml` i18n layer is not implemented yet.

## 10. License

See [LICENSE](LICENSE).
