package com.advancedenchants.core;

import com.advancedenchants.model.ActionDef;
import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.model.Levels;
import com.advancedenchants.model.TriggerDef;
import com.advancedenchants.storage.ItemTags;
import com.advancedenchants.util.Messages;
import com.advancedenchants.util.VersionSafe;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 动作执行器：实现 SCHEMA.md 第 5 节全部动作。
 * 影响事件的数值统一写入 {@link EventMods}，由监听器在事件末尾应用；
 * 世界修改类动作受 config.yml 的 allow-terrain-changes 与单次限流保护。
 */
public final class ActionExec {

    private final org.bukkit.plugin.Plugin plugin;
    private final Random random = new Random();

    /** 安全开关。 */
    public boolean allowTerrainChanges = true;
    public boolean allowCommands = true;
    public boolean allowScripts;
    public boolean protectBlockContainers = true;

    /** 当前正在执行的动作所属等级（供 COMMAND 的 {level} / {value} 占位符使用）。 */
    private int currentLevel = 1;

    public ActionExec(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    public void execute(TriggerCtx ctx, EnchantDef def, TriggerDef td, int level) {
        this.currentLevel = Math.max(1, level);
        for (ActionDef action : td.actions()) {
            try {
                run(ctx, def, action, level);
            } catch (Throwable t) {
                plugin.getLogger().warning("执行动作失败 " + def.id() + " -> " + action.type() + ": " + t);
            }
        }
    }

    private void run(TriggerCtx ctx, EnchantDef def, ActionDef a, int level) {
        switch (a.type()) {
            // ---------------------------------------------------------- 伤害类（写入 mods）
            case "DAMAGE_MULTIPLY" -> {
                if ("SELF".equalsIgnoreCase(a.s("target", ""))) {
                    mods(ctx).multiply("mydmg.mult." + def.id(), scaled(a, level, def));
                } else {
                    mods(ctx).multiply("dmg.mult." + def.id(), scaled(a, level, def));
                }
            }
            case "DAMAGE_ADD" -> {
                if ("SELF".equalsIgnoreCase(a.s("target", ""))) {
                    mods(ctx).add("mydmg.add." + def.id(), scaled(a, level, def));
                } else {
                    mods(ctx).add("dmg.add." + def.id(), scaled(a, level, def));
                }
            }
            case "TRUE_DAMAGE" -> {
                if ("SELF".equalsIgnoreCase(a.s("target", ""))) {
                    mods(ctx).add("mydmg.add." + def.id(), scaled(a, level, def));
                } else {
                    mods(ctx).add("dmg.true", scaled(a, level, def));
                }
            }
            case "PERCENT_CURRENT_HP" -> percentDamage(ctx, a, level, def, true);
            case "PERCENT_MAX_HP" -> percentDamage(ctx, a, level, def, false);
            case "EXECUTE" -> executeStrike(ctx, a, level, def);
            case "LIFESTEAL" -> lifesteal(ctx, a, level, def);
            case "HEAL" -> {
                if (ctx.kind == TriggerCtx.TriggerKind.ON_DAMAGED) {
                    mods(ctx).add("heal.defend", scaled(a, level, def));
                } else {
                    mods(ctx).add("heal.attack", scaled(a, level, def));
                }
            }
            case "DAMAGE_REDUCE" -> {
                // 按附魔 ID 分桶，多个减伤附魔各自乘算叠加
                mods(ctx).set("dmg.reduce." + def.id(), scaled(a, level, def));
            }
            case "DODGE" -> {
                if (chance(scaled(a, level, def))) {
                    mods(ctx).flag(EventMods.Flag.DODGE);
                }
            }
            case "DAMAGE_CAP" -> mods(ctx).set("dmg.cap", scaled(a, level, def));
            case "REFLECT" -> mods(ctx).set("reflect", scaled(a, level, def));
            case "MARK" -> mark(ctx, a, level, def);
            case "ARMOR_DAMAGE" -> armorDamage(ctx, a, level, def);
            case "ARMOR_PIERCE" -> mods(ctx).set("dmg.pierce", scaled(a, level, def));
            case "CONVERT_DROP" -> convertDrop(ctx, a, level, def);
            case "MULTIPLY_DROP" -> multiplyDrop(ctx, a, level, def);

            // ---------------------------------------------------------- 实体 / 状态
            case "POTION" -> applyPotion(ctx, a, level, def);
            case "POTION_SELF" -> applyPotionTarget(ctx.player, a, level, def);
            case "CLEAR_POTION" -> clearPotion(ctx, a, def);
            case "IGNITE" -> ignite(ctx, a, level, def);
            case "EXTINGUISH" -> {
                ctx.player.setFireTicks(0);
                if (ctx.victim != null) {
                    ctx.victim.setFireTicks(0);
                }
            }
            case "KNOCKBACK" -> knockback(ctx, a, level, def);
            case "LAUNCH" -> launch(ctx, a, level, def);
            case "PULL" -> pull(ctx, a, level, def);
            case "TELEPORT" -> teleport(ctx, a, level, def);
            case "SUMMON" -> summon(ctx, a, level, def);
            case "LIGHTNING" -> lightning(ctx, a, level, def);
            case "EXPLOSION" -> explosion(ctx, a, level, def);
            case "POTION_CLOUD" -> potionCloud(ctx, a, level, def);
            case "SPAWN_PROJECTILE" -> spawnProjectile(ctx, a, level, def);
            case "ARROW_RAIN" -> arrowRain(ctx, a, level, def);
            case "SHOCKWAVE" -> shockwave(ctx, a, level, def);
            case "FREEZE" -> freeze(ctx, a, level, def);

            // ---------------------------------------------------------- 世界修改
            case "SET_BLOCK" -> setBlock(ctx, a, def);
            case "BREAK_BLOCK" -> breakBlock(ctx, a, level, def);
            case "VEIN_MINE" -> veinMine(ctx, a, level, def);
            case "TREE_FELL" -> treeFell(ctx, a, def);
            case "SHEAR_AREA" -> shearArea(ctx, a, def);
            case "AUTO_REPLANT" -> autoReplant(ctx);
            case "AUTO_SMELT" -> autoSmelt(ctx, a, def);

            // ---------------------------------------------------------- 物品
            case "DURABILITY" -> durability(ctx, a, level);
            case "SOULS" -> souls(ctx, a, level);
            case "CONSUME_HELD" -> consumeHeld(ctx, a);
            case "CANCEL_EVENT" -> mods(ctx).flag(EventMods.Flag.CANCEL_EVENT);
            case "SURVIVE" -> survive(ctx, a, level);
            case "ATTACK_SPEED" -> {
                mods(ctx).flag(EventMods.Flag.ATTACK_SPEED);
                mods(ctx).set("attack.maxcooldown", Math.max(0.0, a.d("max-cooldown", 1.1)));
            }
            case "SOULBOUND" -> {
                mods(ctx).flag(EventMods.Flag.SOULBOUND);
                for (ItemStack item : ctx.items) {
                    if (item != null && !item.getType().isAir()) {
                        ItemTags.addSouls(item, 0);
                    }
                }
                ctx.state("soulbound.items", new ArrayList<>(ctx.items));
            }

            // ---------------------------------------------------------- 反馈 / 集成
            case "COMMAND" -> command(ctx, a, def);
            case "MESSAGE" -> message(ctx, a, level);
            case "TITLE" -> title(ctx, a, level);
            case "ACTIONBAR" -> actionbar(ctx, a, level);
            case "PARTICLE" -> particle(ctx, a, level);
            case "SOUND" -> sound(ctx, a);
            case "ADD_XP" -> {
                int amount = (int) Math.round(scaled(a, level, def));
                if (amount > 0) {
                    ctx.player.giveExp(amount);
                }
            }
            case "MULTIPLY_XP", "XP_MULTIPLY" -> {
                double factor = scaled(a, level, def);
                mods(ctx).multiply("xp.mult", factor <= 0 ? 1 : factor);
            }
            case "RUN_SCRIPT" -> {
                if (allowScripts) {
                    plugin.getLogger().info("[script] " + a.s("script", "") + " by " + ctx.player.getName());
                }
            }
            default -> {
                // EnchantLoader 已在加载期告警
            }
        }
    }

    // ==================================================================== 伤害类

    private void percentDamage(TriggerCtx ctx, ActionDef a, int level, EnchantDef def, boolean current) {
        if (ctx.victim == null) {
            return;
        }
        double ratio = scaled(a, level, def);
        double base = current ? ctx.victim.getHealth() : ctx.victim.getMaxHealth();
        mods(ctx).add("dmg.true", base * ratio);
    }

    private void executeStrike(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (ctx.victim == null) {
            return;
        }
        double threshold = scaled(a, level, def);
        double ratio = ctx.victim.getHealth() / Math.max(1.0, ctx.victim.getMaxHealth());
        if (ratio <= Math.max(0.01, threshold)) {
            double extra = a.d("damage", 4.0) * level;
            mods(ctx).add("dmg.true", extra);
            effect(ctx, ctx.victim.getLocation(), a.s("particle", "CRIT"), 12);
        }
    }

    private void lifesteal(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        double ratio = scaled(a, level, def);
        double source = ctx.estimatedDamage > 0 ? ctx.estimatedDamage : ctx.damage;
        double heal = Math.max(0, source * ratio);
        if (heal > 0) {
            // 吸血属于"进攻侧收益"，由 applyOffense 结算
            mods(ctx).add("heal.attack", heal);
        }
    }

    private void mark(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (ctx.victim == null) {
            return;
        }
        double vuln = scaled(a, level, def);
        int duration = (int) Math.round(a.d("duration", 100) + a.d("duration-per-level", 40) * (level - 1));
        // 标记写入目标实体的 PDC：攻击方在造成伤害前读取，重启后依然有效
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString("advancedenchants:mark_vuln");
            org.bukkit.NamespacedKey untilKey = org.bukkit.NamespacedKey.fromString("advancedenchants:mark_until");
            if (key != null && untilKey != null) {
                var pdc = ctx.victim.getPersistentDataContainer();
                pdc.set(key, org.bukkit.persistence.PersistentDataType.DOUBLE, Math.min(2.0, vuln));
                pdc.set(untilKey, org.bukkit.persistence.PersistentDataType.LONG,
                        System.currentTimeMillis() + duration * 50L);
            }
        } catch (Throwable ignored) {
            // 部分实体类型不支持 PDC 时退化为本次伤害加成
            mods(ctx).add("dmg.true", Math.max(0, ctx.damage * vuln));
        }
        effect(ctx, ctx.victim.getLocation(), "ENCHANT", 8);
    }

    private void armorDamage(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (ctx.victim == null) {
            return;
        }
        // LivingEntity#getEquipment() 对无装备槽实体可能返回 null，必须判空
        org.bukkit.inventory.EntityEquipment equipment = ctx.victim.getEquipment();
        if (equipment == null) {
            return;
        }
        int amount = (int) Math.round(scaled(a, level, def));
        for (ItemStack armor : equipment.getArmorContents()) {
            if (armor == null || armor.getType().isAir() || armor.getType().getMaxDurability() <= 0) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta meta = armor.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setDamage(Math.min(armor.getType().getMaxDurability() - 1,
                        damageable.getDamage() + amount));
                armor.setItemMeta(meta);
            }
        }
    }

    private void convertDrop(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        List<ItemStack> drops = ctx.drops;
        if (drops == null || drops.isEmpty()) {
            return;
        }
        Material material = material(a.s("material", "GOLD_INGOT"));
        if (material == null) {
            return;
        }
        double chance = scaled(a, level, def);
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (chance >= 1.0 || random.nextDouble() < chance) {
                drops.set(i, new ItemStack(material, drop.getAmount()));
            }
        }
    }

    private void multiplyDrop(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        List<ItemStack> drops = ctx.drops;
        if (drops == null || drops.isEmpty()) {
            return;
        }
        double factor = scaled(a, level, def);
        if (factor <= 1.0) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            int amount = (int) Math.min(64, Math.floor(drop.getAmount() * (factor - 1.0)));
            if (amount <= 0) {
                continue;
            }
            int max = drop.getType().getMaxStackSize();
            while (amount > 0) {
                int give = Math.min(max, amount);
                extra.add(new ItemStack(drop.getType(), give));
                amount -= give;
            }
        }
        drops.addAll(extra);
    }

    // ==================================================================== 实体 / 状态

    private void applyPotion(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        String who = a.s("who", "TARGET").toUpperCase(Locale.ROOT);
        LivingEntity target = switch (who) {
            case "SELF" -> ctx.player;
            case "TARGET" -> ctx.victim != null ? ctx.victim : ctx.player;
            default -> ctx.victim != null ? ctx.victim : ctx.player;
        };
        applyPotionTarget(target, a, level, def);
    }

    private void applyPotionTarget(LivingEntity target, ActionDef a, int level, EnchantDef def) {
        if (target == null) {
            return;
        }
        PotionEffectType type = VersionSafe.potion(a.s("potion", "POISON"));
        if (type == null) {
            return;
        }
        int duration = (int) Math.round(a.d("duration", 60) + a.d("duration-per-level", 20) * (level - 1));
        int amplifier = (int) Math.round(a.d("amplifier", 0) + a.d("amplifier-per-level", 0) * (level - 1));
        try {
            target.addPotionEffect(new PotionEffect(type, Math.max(1, duration), Math.max(0, Math.min(9, amplifier)), false, true, true));
        } catch (Throwable ignored) {
        }
    }

    private void clearPotion(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        String who = a.s("who", "SELF").toUpperCase(Locale.ROOT);
        LivingEntity target = "TARGET".equals(who) && ctx.victim != null ? ctx.victim : ctx.player;
        PotionEffectType type = VersionSafe.potion(a.s("potion", "POISON"));
        if (type != null) {
            target.removePotionEffect(type);
        } else {
            for (PotionEffect effect : target.getActivePotionEffects()) {
                target.removePotionEffect(effect.getType());
            }
        }
    }

    private void ignite(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        int seconds = (int) Math.round(scaled(a, level, def));
        String who = a.s("who", "TARGET").toUpperCase(Locale.ROOT);
        LivingEntity target = "SELF".equals(who) ? ctx.player : (ctx.victim != null ? ctx.victim : null);
        if (target != null) {
            target.setFireTicks(Math.max(target.getFireTicks(), seconds * 20));
        }
    }

    private void knockback(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        Entity target = "SELF".equalsIgnoreCase(a.s("who", "TARGET")) ? ctx.player : ctx.victim;
        if (target == null) {
            return;
        }
        double power = scaled(a, level, def);
        Vector dir = target.getLocation().toVector().subtract(ctx.player.getLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            dir = ctx.player.getLocation().getDirection().clone();
        }
        dir = dir.normalize().multiply(power).setY(Math.max(0.25, power * 0.35));
        target.setVelocity(target.getVelocity().add(dir));
    }

    private void launch(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        Entity target = "SELF".equalsIgnoreCase(a.s("who", "TARGET")) ? ctx.player : ctx.victim;
        if (target == null) {
            return;
        }
        double power = scaled(a, level, def);
        target.setVelocity(target.getVelocity().setY(power));
    }

    private void pull(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (ctx.victim == null) {
            return;
        }
        double power = scaled(a, level, def);
        Vector dir = ctx.player.getLocation().toVector().subtract(ctx.victim.getLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        ctx.victim.setVelocity(dir.normalize().multiply(Math.min(1.5, power)));
    }

    private void teleport(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        String target = a.s("target", "TARGET").toUpperCase(Locale.ROOT);
        double distance = scaled(a, level, def);
        if ("TARGET".equals(target) && ctx.victim != null) {
            Location to = ctx.victim.getLocation().clone();
            Vector behind = ctx.victim.getLocation().getDirection().clone().multiply(-1);
            to.add(behind.normalize().multiply(1.2));
            safeTeleport(ctx.player, to);
        } else if ("ARROW".equals(target) && ctx.projectile != null) {
            safeTeleport(ctx.player, ctx.projectile.getLocation().clone().add(0, 1, 0));
        } else if ("BEHIND".equals(target)) {
            Vector back = ctx.player.getLocation().getDirection().clone().multiply(-distance);
            safeTeleport(ctx.player, ctx.player.getLocation().clone().add(back));
        }
    }

    private void safeTeleport(Player player, Location to) {
        World world = to.getWorld();
        if (world == null) {
            return;
        }
        for (int dy = 1; dy >= -2; dy--) {
            Location candidate = to.clone().add(0, dy, 0);
            Block feet = candidate.getBlock();
            Block head = candidate.clone().add(0, 1, 0).getBlock();
            Block below = candidate.clone().add(0, -1, 0).getBlock();
            if (feet.isPassable() && head.isPassable() && !below.isPassable()) {
                player.teleport(candidate);
                return;
            }
        }
        player.teleport(to);
    }

    private void summon(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        String name = a.s("entity", "");
        EntityType type = VersionSafe.enumValue(EntityType.class, name);
        if (type == null || !type.isSpawnable()) {
            return;
        }
        if (type == EntityType.CREEPER || type == EntityType.PRIMED_TNT || type == EntityType.ENDER_CRYSTAL) {
            return; // 安全黑名单：不做地形破坏型召唤
        }
        int count = (int) Math.round(a.d("count", 1) + a.d("count-per-level", 0) * (level - 1));
        count = Math.max(1, Math.min(6, count));
        int lifetime = (int) Math.round(a.d("lifetime", 10));
        Location base = ctx.target != null ? ctx.target.getLocation() : ctx.block != null
                ? ctx.block.getLocation().add(0.5, 1, 0.5) : ctx.player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Entity spawned = world.spawnEntity(base.clone().add(random.nextDouble() - 0.5, 0.2, random.nextDouble() - 0.5), type);
            if (lifetime > 0 && spawned instanceof LivingEntity living) {
                plugin.getServer().getScheduler().runTaskLater(plugin, living::remove, lifetime * 20L);
            }
        }
    }

    private void lightning(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        // SCHEMA 规定用 base（0~1 概率）；为兼容旧配置同时接受 chance 字段
        double chance = a.has("base") ? scaled(a, level, def) : a.d("chance", 1.0);
        if (chance < 1.0 && random.nextDouble() > chance) {
            return;
        }
        Entity anchor = ctx.victim != null ? ctx.victim : ctx.target;
        Location loc = anchor != null ? anchor.getLocation() : ctx.player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        if (a.bool("damage", false)) {
            world.strikeLightning(loc);
        } else {
            world.strikeLightningEffect(loc);
        }
    }

    private void explosion(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        Entity anchor = ctx.victim != null ? ctx.victim : ctx.target;
        Location loc = anchor != null ? anchor.getLocation() : ctx.player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        // 尊重配置的 base/per-level/max（半径即强度）；未配置时退化为按等级估算
        double configured = scaled(a, level, def);
        float power = (float) Math.min(6.0, configured > 0 ? configured : 1.0 + level * 0.6);
        boolean fire = a.bool("set-fire", false) && allowTerrainChanges;
        boolean breakBlocks = a.bool("break-blocks", false) && allowTerrainChanges;
        if (breakBlocks) {
            world.createExplosion(loc, power, fire, true, ctx.player);
        } else {
            // 粒子按候选名解析（先新后旧），并自动跨版本别名回退
            effectAt(world, loc, new String[]{"EXPLOSION", "EXPLOSION_NORMAL", "EXPLOSION_EMITTER"}, 1, 0);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
            double radius = Math.max(1.5, power);
            for (Entity entity : world.getNearbyEntities(loc, radius, 3, radius)) {
                if (entity.equals(ctx.player) || !(entity instanceof LivingEntity living) || living.equals(ctx.victim)) {
                    continue;
                }
                living.damage(Math.max(2.0, power * 3), ctx.player);
            }
            // 受害者伤害：默认造成，可用 self-damage: false 关闭
            if (ctx.victim != null && a.bool("self-damage", true) && !ctx.victim.equals(ctx.player)) {
                ctx.victim.damage(Math.max(2.0, power * 2), ctx.player);
            }
        }
    }

    private void potionCloud(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        Entity anchor = ctx.victim != null ? ctx.victim : ctx.player;
        Location loc = anchor.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        PotionEffectType type = VersionSafe.potion(a.s("potion", "POISON"));
        if (type == null) {
            return;
        }
        int duration = (int) Math.round(a.d("duration", 100));
        float radius = (float) Math.max(1.0, a.d("radius", 2.5));
        int amplifier = (int) Math.round(a.d("amplifier", 0));
        AreaEffectCloud cloud = world.spawn(loc, AreaEffectCloud.class);
        cloud.setRadius(radius);
        cloud.setDuration(Math.max(20, duration));
        cloud.setSource(ctx.player);
        applyCloudPotion(cloud, type, duration, amplifier);
    }

    /**
     * 基础药水类型：1.20.5+ 用 setBasePotionType(PotionEffectType)，旧版用 setBasePotionData(PotionData)。
     * 两者签名不同，为避免跨版本编译冲突改为反射调用；失败也不影响自定义药水效果。
     */
    private void applyCloudPotion(AreaEffectCloud cloud, PotionEffectType type, int duration, int amplifier) {
        try {
            java.lang.reflect.Method method = AreaEffectCloud.class.getMethod("setBasePotionType", PotionEffectType.class);
            method.invoke(cloud, type);
        } catch (Throwable ignored) {
            try {
                Object potionData = Class.forName("org.bukkit.potion.PotionData")
                        .getConstructor(PotionEffectType.class).newInstance(type);
                AreaEffectCloud.class.getMethod("setBasePotionData", potionData.getClass())
                        .invoke(cloud, potionData);
            } catch (Throwable ignoredToo) {
                // 均不可用：依赖下面的 addCustomEffect
            }
        }
        try {
            cloud.addCustomEffect(new PotionEffect(type, Math.max(20, duration),
                    Math.max(0, Math.min(4, amplifier))), true);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    private void spawnProjectile(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        EntityType type = VersionSafe.enumValue(EntityType.class, a.s("projectile", "ARROW"));
        if (type == null) {
            return;
        }
        int count = (int) Math.round(a.d("count", 1) + a.d("count-per-level", 0) * (level - 1));
        count = Math.max(1, Math.min(10, count));
        double speed = Math.max(0.5, a.d("speed", 2.0));
        double spread = Math.max(0.0, a.d("spread", 0.1));
        Vector direction = ctx.player.getLocation().getDirection();
        for (int i = 0; i < count; i++) {
            Vector dir = direction.clone().add(new Vector(
                    (random.nextDouble() - 0.5) * spread,
                    (random.nextDouble() - 0.5) * spread,
                    (random.nextDouble() - 0.5) * spread)).normalize().multiply(speed);
            try {
                Class<? extends Projectile> clazz = type.getEntityClass() != null
                        && Projectile.class.isAssignableFrom(type.getEntityClass())
                        ? type.getEntityClass().asSubclass(Projectile.class) : Arrow.class;
                Projectile projectile = ctx.player.getWorld().spawn(ctx.player.getEyeLocation(), clazz);
                projectile.setVelocity(dir);
                projectile.setShooter(ctx.player);
            } catch (Throwable t) {
                Arrow arrow = ctx.player.launchProjectile(Arrow.class, dir);
                arrow.setShooter(ctx.player);
            }
        }
    }

    private void arrowRain(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        int count = Math.max(1, Math.min(24, (int) Math.round(a.d("count", 5) + level)));
        Location center = (ctx.victim != null ? ctx.victim.getLocation() : ctx.player.getLocation()).clone();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radius = Math.max(1.0, a.d("radius", 3.0));
        for (int i = 0; i < count; i++) {
            final int index = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location spawn = center.clone().add(
                        (random.nextDouble() - 0.5) * radius * 2,
                        6 + random.nextDouble() * 3,
                        (random.nextDouble() - 0.5) * radius * 2);
                Arrow arrow = world.spawnArrow(spawn, new Vector(0, -1, 0), (float) Math.max(1.0, a.d("speed", 2.2)), 4f);
                arrow.setShooter(ctx.player);
                arrow.setDamage(Math.max(1.0, a.d("damage", 3.0)));
            }, index * 2L);
        }
    }

    private void shockwave(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        double radius = Math.max(1.5, a.d("radius", 3.0));
        double damage = Math.max(1.0, a.d("damage", 4.0));
        double knock = Math.max(0.0, a.d("knockback", 1.0));
        Location center = ctx.player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        effectAt(world, center, new String[]{"EXPLOSION", "EXPLOSION_NORMAL", "POOF"}, 6, 1.0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
        for (Entity entity : world.getNearbyEntities(center, radius, 3, radius)) {
            if (entity.equals(ctx.player) || !(entity instanceof LivingEntity living)) {
                continue;
            }
            living.damage(damage, ctx.player);
            if (knock > 0) {
                Vector push = living.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() > 0.01) {
                    living.setVelocity(push.normalize().multiply(knock).setY(0.4));
                }
            }
        }
    }

    private void freeze(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (ctx.victim == null) {
            return;
        }
        int duration = (int) Math.round(a.d("duration", 60) + a.d("duration-per-level", 20) * (level - 1));
        ctx.victim.setFreezeTicks(Math.max(ctx.victim.getFreezeTicks(), duration));
        double tickDamage = a.d("tick-damage", 0.5);
        if (tickDamage > 0) {
            mods(ctx).add("dmg.true", tickDamage * level);
        }
    }

    // ==================================================================== 世界修改

    private void setBlock(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        if (!allowTerrainChanges) {
            return;
        }
        Material material = material(a.s("material", "STONE"));
        if (material == null || !material.isBlock()) {
            return;
        }
        Block base = ctx.block;
        if (base == null) {
            return;
        }
        // chance 支持两种写法：0~1 比例，或 >1 的百分数（配置里常见 chance: 100）
        double raw = a.d("chance", 1.0);
        double chance = raw > 1.0 ? Math.min(1.0, raw / 100.0) : Math.max(0.0, raw);
        if (chance < 1.0 && random.nextDouble() > chance) {
            return;
        }
        // radius：以点击方块为中心的方形区域（默认 0 = 只改原方块）
        int radius = Math.max(0, Math.min(4, a.i("radius", 0)));
        int placed = 0;
        int limit = Math.max(1, Math.min(64, a.i("limit", 32)));
        for (int x = -radius; x <= radius && placed < limit; x++) {
            for (int y = -radius; y <= radius && placed < limit; y++) {
                for (int z = -radius; z <= radius && placed < limit; z++) {
                    Block target = base.getRelative(x, y, z);
                    if (protectBlockContainers && target.getState() instanceof org.bukkit.inventory.InventoryHolder) {
                        continue;
                    }
                    if (target.getType() == material) {
                        continue;
                    }
                    target.setType(material, true);
                    placed++;
                }
            }
        }
    }

    private void breakBlock(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (!allowTerrainChanges) {
            return;
        }
        Block base = ctx.block;
        if (base == null) {
            return;
        }
        boolean drops = a.bool("drops", false);
        int limit = Math.max(1, Math.min(64, a.i("limit", 8)));
        int radius = Math.max(1, Math.min(3, a.i("radius", 1)));
        int done = 0;
        for (int x = -radius; x <= radius && done < limit; x++) {
            for (int y = -radius; y <= radius && done < limit; y++) {
                for (int z = -radius; z <= radius && done < limit; z++) {
                    Block block = base.getRelative(x, y, z);
                    if (!breakable(block)) {
                        continue;
                    }
                    if (drops) {
                        block.breakNaturally(ctx.player.getInventory().getItemInMainHand());
                    } else {
                        block.setType(Material.AIR, false);
                    }
                    done++;
                }
            }
        }
    }

    private void veinMine(TriggerCtx ctx, ActionDef a, int level, EnchantDef def) {
        if (!allowTerrainChanges) {
            return;
        }
        Block base = ctx.block;
        if (base == null) {
            return;
        }
        Material origin = base.getType();
        int limit = Math.max(1, Math.min(96, a.i("limit", 24)));
        int radius = Math.max(1, Math.min(4, a.i("radius", 2)));
        boolean drops = a.bool("drops", true);
        boolean sameType = a.bool("same-type", true);
        List<Block> found = new ArrayList<>();
        java.util.ArrayDeque<Block> queue = new java.util.ArrayDeque<>();
        java.util.Set<Location> visited = new java.util.HashSet<>();
        queue.add(base);
        while (!queue.isEmpty() && found.size() < limit) {
            Block current = queue.poll();
            if (current == null || !visited.add(current.getLocation())) {
                continue;
            }
            if (current.getType().isAir()) {
                continue;
            }
            if (sameType && current.getType() != origin) {
                continue;
            }
            if (!breakable(current)) {
                continue;
            }
            found.add(current);
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                    BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block next = current.getRelative(face);
                if (Math.abs(next.getX() - base.getX()) <= radius
                        && Math.abs(next.getY() - base.getY()) <= radius
                        && Math.abs(next.getZ() - base.getZ()) <= radius) {
                    queue.add(next);
                }
            }
        }
        for (Block block : found) {
            if (block.equals(base)) {
                continue; // 原方块由原版逻辑处理
            }
            if (drops) {
                block.breakNaturally(ctx.player.getInventory().getItemInMainHand());
            } else {
                block.setType(Material.AIR, false);
            }
        }
    }

    private void treeFell(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        if (!allowTerrainChanges) {
            return;
        }
        Block base = ctx.block;
        if (base == null) {
            return;
        }
        int limit = Math.max(1, Math.min(120, a.i("limit", 64)));
        List<Block> logs = new ArrayList<>();
        java.util.ArrayDeque<Block> queue = new java.util.ArrayDeque<>();
        java.util.Set<Location> visited = new java.util.HashSet<>();
        queue.add(base);
        while (!queue.isEmpty() && logs.size() < limit) {
            Block current = queue.poll();
            if (current == null || !visited.add(current.getLocation())) {
                continue;
            }
            Material type = current.getType();
            if (!isLog(type) && !isLeaves(type)) {
                continue;
            }
            logs.add(current);
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                    BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
                queue.add(current.getRelative(face));
            }
        }
        int broken = 0;
        for (Block block : logs) {
            if (block.equals(base)) {
                continue;
            }
            if (!isLeaves(block.getType())) {
                block.breakNaturally(ctx.player.getInventory().getItemInMainHand());
                if (++broken >= limit) {
                    break;
                }
            } else {
                block.setType(Material.AIR, false);
            }
        }
    }

    private void shearArea(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        int radius = Math.max(1, Math.min(4, a.i("radius", 2)));
        Location center = ctx.block != null ? ctx.block.getLocation() : ctx.player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(center.clone().add(x, y, z));
                    String name = block.getType().name();
                    if (name.endsWith("_WOOL") && count < 32) {
                        block.breakNaturally(ctx.player.getInventory().getItemInMainHand());
                        count++;
                    }
                }
            }
        }
    }

    private void autoReplant(TriggerCtx ctx) {
        if (!allowTerrainChanges) {
            return;
        }
        Block base = ctx.block;
        if (base == null) {
            return;
        }
        Material below = base.getRelative(BlockFace.DOWN).getType();
        if (below != Material.FARMLAND && below != Material.SOUL_SAND) {
            return;
        }
        Material replant = switch (base.getType()) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROTS;
            case POTATOES -> Material.POTATOES;
            case BEETROOTS -> Material.BEETROOTS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
        if (replant != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> base.setType(replant, false));
        }
    }

    private void autoSmelt(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        List<ItemStack> drops = ctx.drops;
        if (drops == null || drops.isEmpty()) {
            return;
        }
        int multiplier = Math.max(1, a.i("multiplier", 1));
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            Material smelted = smeltResult(drop.getType());
            if (smelted != null) {
                drops.set(i, new ItemStack(smelted, drop.getAmount() * multiplier));
            }
        }
    }

    private Material smeltResult(Material input) {
        String name = input.name();
        return switch (name) {
            case "IRON_ORE", "DEEPSLATE_IRON_ORE", "RAW_IRON" -> Material.IRON_INGOT;
            case "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE", "RAW_GOLD" -> Material.GOLD_INGOT;
            case "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "RAW_COPPER" -> Material.COPPER_INGOT;
            case "SAND" -> Material.GLASS;
            case "COBBLESTONE", "STONE" -> Material.STONE;
            case "CLAY_BALL" -> Material.BRICK;
            case "ANCIENT_DEBRIS" -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    // ==================================================================== 物品

    private void durability(TriggerCtx ctx, ActionDef a, int level) {
        double delta = scaled(a, level, new EnchantDef("", "", com.advancedenchants.model.Tier.COMMON, 1,
                java.util.Set.of(), List.of(), List.of(), 1, java.util.Map.of()));
        for (ItemStack item : ctx.items) {
            if (item == null || item.getType().isAir() || item.getType().getMaxDurability() <= 0) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                int newDamage = (int) Math.round(damageable.getDamage() - delta);
                damageable.setDamage(Math.max(0, Math.min(item.getType().getMaxDurability(), newDamage)));
                item.setItemMeta(meta);
            }
        }
    }

    private void souls(TriggerCtx ctx, ActionDef a, int level) {
        // 允许负值（消耗灵魂）：这里不能用钳到 >=0 的 plain()
        int delta = (int) Math.round(a.d("base", 1) + a.d("per-level", 0) * (level - 1));
        if (delta == 0) {
            return;
        }
        for (ItemStack item : ctx.items) {
            if (item != null && ItemTags.hasAny(item)) {
                ItemTags.addSouls(item, delta);
            }
        }
    }

    private void consumeHeld(TriggerCtx ctx, ActionDef a) {
        ItemStack hand = ctx.player.getInventory().getItemInMainHand();
        int amount = Math.max(1, a.i("amount", 1));
        hand.setAmount(Math.max(0, hand.getAmount() - amount));
    }

    private void survive(TriggerCtx ctx, ActionDef a, int level) {
        mods(ctx).flag(EventMods.Flag.SURVIVE);
        mods(ctx).set("survive.health", Math.max(0.5, a.d("health", 1.0)));
        int invulnerable = (int) Math.round(a.d("invulnerable", 1.0));
        if (invulnerable > 0) {
            ctx.player.setNoDamageTicks(Math.max(ctx.player.getNoDamageTicks(), invulnerable * 20));
        }
    }

    private void command(TriggerCtx ctx, ActionDef a, EnchantDef def) {
        if (!allowCommands) {
            return;
        }
        String raw = a.s("value", "");
        if (raw.isBlank()) {
            return;
        }
        String cmd = raw.replace("{player}", ctx.player.getName())
                .replace("{target}", ctx.victim != null ? ctx.victim.getName() : ctx.player.getName())
                .replace("{level}", String.valueOf(currentLevel))
                .replace("{value}", Messages.num(scaled(a, currentLevel, def)))
                .replace("{enchant}", def.id());
        if ("PLAYER".equalsIgnoreCase(a.s("as", "CONSOLE"))) {
            ctx.player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        } else {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
        }
    }

    private void message(TriggerCtx ctx, ActionDef a, int level) {
        String raw = a.s("value", "");
        if (raw.isBlank()) {
            return;
        }
        String text = raw.replace("{value}", Messages.num(scaled(a, level, new EnchantDef("", "",
                com.advancedenchants.model.Tier.COMMON, 1, java.util.Set.of(), List.of(), List.of(), 1,
                java.util.Map.of()))));
        String who = a.s("who", "SELF").toUpperCase(Locale.ROOT);
        switch (who) {
            case "TARGET" -> Messages.sendRaw(ctx.victim, Messages.color(text));
            case "NEARBY" -> {
                for (Player p : ctx.player.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(ctx.player.getLocation()) <= 400) {
                        Messages.sendRaw(p, Messages.color(text));
                    }
                }
            }
            default -> Messages.sendRaw(ctx.player, Messages.color(text));
        }
    }

    private void title(TriggerCtx ctx, ActionDef a, int level) {
        String title = Messages.color(a.s("title", ""));
        String subtitle = Messages.color(a.s("subtitle", ""));
        int fadeIn = (int) Math.round(a.d("fade-in", 10));
        int stay = (int) Math.round(a.d("stay", 40));
        int fadeOut = (int) Math.round(a.d("fade-out", 10));
        try {
            ctx.player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (Throwable t) {
            // 1.21 的 Paper 需要 Component 重载，这里退化为聊天消息
            Messages.sendRaw(ctx.player, title + (subtitle.isBlank() ? "" : " &7" + subtitle));
        }
    }

    private void actionbar(TriggerCtx ctx, ActionDef a, int level) {
        String text = Messages.color(a.s("value", ""));
        // sendActionBar(String) 在 1.20.4 绑定到 Bungee 的 BaseComponent，
        // 在 1.21 绑定到 Adventure，签名不同，因此按运行时可用重载反射调用。
        for (java.lang.reflect.Method method : ctx.player.getClass().getMethods()) {
            if (!method.getName().equals("sendActionBar") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].getName().equals("net.md_5.bungee.api.chat.BaseComponent")) {
                continue;
            }
            try {
                method.invoke(ctx.player, text);
                return;
            } catch (Throwable ignored) {
                // 尝试下一个重载
            }
        }
    }

    private void particle(TriggerCtx ctx, ActionDef a, int level) {
        Particle particle = VersionSafe.enumValue(Particle.class, a.s("particle", "CRIT"));
        if (particle == null) {
            return;
        }
        int count = Math.max(1, Math.min(200, a.i("count", 8)));
        double spread = Math.max(0.0, a.d("spread", 0.4));
        double speed = Math.max(0.0, a.d("speed", 0.05));
        Location at = switch (a.s("who", "TARGET").toUpperCase(Locale.ROOT)) {
            case "SELF" -> ctx.player.getLocation().add(0, 1, 0);
            case "BLOCK" -> ctx.block != null ? ctx.block.getLocation().add(0.5, 0.5, 0.5) : ctx.player.getLocation();
            case "LOCATION" -> ctx.player.getLocation();
            default -> ctx.victim != null ? ctx.victim.getLocation().add(0, 1, 0) : ctx.player.getLocation();
        };
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(particle, at, count, spread, spread, spread, speed);
    }

    private void sound(TriggerCtx ctx, ActionDef a) {
        Sound sound = VersionSafe.enumValue(Sound.class, a.s("sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        if (sound == null) {
            return;
        }
        float volume = (float) Math.max(0.0, a.d("volume", 0.7));
        float pitch = (float) Math.max(0.1, a.d("pitch", 1.0));
        Location at = switch (a.s("who", "SELF").toUpperCase(Locale.ROOT)) {
            case "TARGET" -> ctx.victim != null ? ctx.victim.getLocation() : ctx.player.getLocation();
            case "BLOCK" -> ctx.block != null ? ctx.block.getLocation() : ctx.player.getLocation();
            default -> ctx.player.getLocation();
        };
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, sound, volume, pitch);
        }
    }

    // ==================================================================== 工具

    private EventMods mods(TriggerCtx ctx) {
        return ctx.mods;
    }

    private double scaled(ActionDef a, int level, EnchantDef def) {
        return Levels.scaled(a.data(), level, def.maxLevel());
    }

    /** 用于外部（如 description 渲染）计算动作数值。 */
    public static double value(ActionDef a, int level, int maxLevel) {
        return Levels.scaled(a.data(), level, maxLevel);
    }

    private boolean chance(double ratio) {
        if (ratio <= 0) {
            return false;
        }
        return ratio >= 1.0 || random.nextDouble() < ratio;
    }

    private void effect(TriggerCtx ctx, Location loc, String particleName, int count) {
        Particle particle = VersionSafe.enumValue(Particle.class, particleName);
        if (particle != null && loc.getWorld() != null) {
            loc.getWorld().spawnParticle(particle, loc, count, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** 按候选名依次解析粒子（跨版本名称差异），全部失败则静默跳过。 */
    private Particle resolveParticle(String... candidates) {
        for (String name : candidates) {
            if (name == null) {
                continue;
            }
            Particle particle = VersionSafe.enumValue(Particle.class, name);
            if (particle != null) {
                return particle;
            }
        }
        return null;
    }

    private void effectAt(World world, Location loc, String[] candidates, int count, double spread) {
        Particle particle = resolveParticle(candidates);
        if (particle != null) {
            world.spawnParticle(particle, loc, count, spread, spread, spread, 0);
        }
    }

    private Material material(String name) {
        Material material = VersionSafe.enumValue(Material.class, name);
        return material != null && material.isBlock() ? material : material;
    }

    private boolean breakable(Block block) {
        if (block == null || block.getType().isAir()) {
            return false;
        }
        if (!allowTerrainChanges) {
            return false;
        }
        if (protectBlockContainers && block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
            return false;
        }
        String name = block.getType().name();
        return !name.equals("BEDROCK") && !name.equals("END_PORTAL_FRAME") && !name.equals("OBSIDIAN")
                && !name.equals("CRYING_OBSIDIAN") && !name.endsWith("SHULKER_BOX") && !name.equals("SPAWNER");
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE") || name.equals("MANGROVE_ROOTS");
    }

    private boolean isLeaves(Material material) {
        return material.name().endsWith("_LEAVES");
    }
}
