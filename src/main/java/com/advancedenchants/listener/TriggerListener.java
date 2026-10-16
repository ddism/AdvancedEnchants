package com.advancedenchants.listener;

import com.advancedenchants.core.Engine;
import com.advancedenchants.core.EventMods;
import com.advancedenchants.core.TriggerCtx;
import com.advancedenchants.model.Trigger;
import com.advancedenchants.storage.ItemTags;
import com.advancedenchants.util.Messages;
import com.advancedenchants.util.VersionSafe;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 触发器总线：把 Bukkit 事件翻译为 {@link TriggerCtx} 并交给 {@link Engine}。
 *
 * 设计要点：
 * - 所有附魔动作只写 EventMods，事件末尾统一 apply，避免互相覆盖；
 * - 物品的耐久/附魔数据修改在事件之后统一回写主手，避免被 Bukkit 覆盖；
 * - 高频事件（ON_MINE_TICK / ON_MOVE / ON_BOW_CHARGE）有节流。
 */
public final class TriggerListener implements Listener {

    private final Plugin plugin;
    private final Engine engine;

    /** 是否允许磨刀石剥离自定义附魔（config.yml: grindstone.strip-enabled）。 */
    private boolean stripEnabled = true;

    public TriggerListener(Plugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public void setStripEnabled(boolean value) {
        this.stripEnabled = value;
    }

    // ==================================================================== 战斗

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player p) {
                attacker = p;
            } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
            try {
                Entity causing = byEntity.getDamageSource().getCausingEntity();
                if (causing instanceof Player p) {
                    attacker = p;
                }
            } catch (Throwable ignored) {
                // 旧版本兼容
            }
        }
        if (attacker == null && !(victim instanceof Player)) {
            return;
        }

        // 进攻方（主手 + 副手）：只影响"打出的伤害"
        double afterAttack = event.getDamage();
        boolean projectileAttack = isProjectileAttack(event);
        if (attacker != null && !projectileAttack) {
            TriggerCtx attackCtx = ctx(attacker, TriggerCtx.TriggerKind.ON_HIT, event);
            attackCtx.victim = victim;
            attackCtx.target = victim;
            attackCtx.damage = event.getDamage();
            attackCtx.estimatedDamage = event.getDamage();
            // MARK（易伤标记）在有效期内为攻击者提供额外伤害
            double markVuln = activeMark(victim);
            if (markVuln > 0) {
                attackCtx.state("mark.vuln", markVuln);
            }
            afterAttack = applyOffense(attackCtx, event, attacker);
        }

        // 防守方（全部盔甲 + 副手）：只影响"受到的伤害"，与进攻方完全隔离
        if (victim instanceof Player defender) {
            TriggerCtx defendCtx = new TriggerCtx(defender, TriggerCtx.TriggerKind.ON_DAMAGED, event);
            defendCtx.victim = defender;
            defendCtx.target = attacker;
            defendCtx.damage = afterAttack;
            defendCtx.estimatedDamage = afterAttack;
            defendCtx.items.addAll(defenseItems(defender));
            afterAttack = applyDefense(defendCtx, event, defender, afterAttack);
        }

        // ON_SHIELD_BLOCK：举盾格挡时单独触发一次。
        // 只跑「在 ON_SHIELD_BLOCK 上定义了动作」的物品 —— 否则盾牌/盔甲会被 ON_DAMAGED 与这里
        // 各结算一次，同一件附魔的减伤翻倍生效（盾牌同时存在于副手与盔甲列表里）。
        if (victim instanceof Player blocker && blocker.isBlocking()
                && engine.registry().anyDefinesTrigger(Trigger.ON_SHIELD_BLOCK)) {
            TriggerCtx shieldCtx = new TriggerCtx(blocker, TriggerCtx.TriggerKind.ON_SHIELD_BLOCK, event);
            shieldCtx.victim = blocker;
            shieldCtx.target = attacker;
            shieldCtx.damage = afterAttack;
            shieldCtx.estimatedDamage = afterAttack;
            shieldCtx.items.addAll(engine.registry().filterItemsByTrigger(
                    defenseItems(blocker), Trigger.ON_SHIELD_BLOCK));
            afterAttack = applyDefense(shieldCtx, event, blocker, afterAttack);
        }

        event.setDamage(Math.max(0, afterAttack));

        // 防守侧已包含 ON_DAMAGED；这里只补 ON_FALL_DAMAGE 专属效果（acrobat / aero_armor / phoenix_wing）
        if (victim instanceof Player faller && isFallDamage(event)) {
            TriggerCtx fallCtx = new TriggerCtx(faller, TriggerCtx.TriggerKind.ON_FALL_DAMAGE, event);
            fallCtx.victim = faller;
            fallCtx.damage = event.getDamage();
            fallCtx.estimatedDamage = event.getDamage();
            fallCtx.items.addAll(gearItems(faller));
            EventMods fallMods = engine.fire(fallCtx);
            // 只应用倍率与闪避：避免把 base/per-level 的加算值在已经算过一遍的伤害上再加一次
            double scaledDamage = fallMods.negatesDamage()
                    ? 0
                    : event.getDamage() * fallMods.offenseFactor();
            double heal = fallMods.get("heal") + fallMods.get("heal.defend");
            if (heal > 0) {
                faller.setHealth(Math.min(faller.getMaxHealth(), faller.getHealth() + heal));
            }
            event.setDamage(Math.max(0, scaledDamage));
        }
    }

    /** 判定是否为摔落伤害（跨版本安全）。 */
    private static boolean isFallDamage(EntityDamageEvent event) {
        try {
            return event.getCause() == EntityDamageEvent.DamageCause.FALL;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 本次伤害是否来自弹射物。
     *
     * 弹射物命中会先触发 ON_PROJECTILE_HIT（含 DAMAGE_MULTIPLY 等），
     * 随后才产生这次 EntityDamageEvent。如果这里再跑一遍 ON_HIT，
     * 同一发箭矢的加成会被计算两次，所以弹射物伤害只走 ON_PROJECTILE_HIT 通道。
     */
    private static boolean isProjectileAttack(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Projectile;
    }

    /**
     * 进攻侧结算：DAMAGE_MULTIPLY / DAMAGE_ADD / TRUE_DAMAGE / 易伤 / 吸血 / 反伤。
     * 绝不读取 dmg.reduce，因此不会把防守方的减伤算到自己的出伤上。
     */
    private double applyOffense(TriggerCtx ctx, EntityDamageEvent event, Player player) {
        EventMods mods = engine.fire(ctx);

        if (mods.flagged(EventMods.Flag.DODGE)) {
            // 进攻方闪避：只取消本次出伤，事件本身仍交给防守方结算
            return 0;
        }
        double mark = ctx.number("mark.vuln", 0.0);
        if (mark > 0) {
            mods.add("dmg.true", Math.max(0.0, event.getDamage() * mark));
        }
        // 只应用进攻侧修正（倍率 / 加算 / 真实伤害），绝不读防守方的 dmg.reduce.*
        double result = mods.applyOffenseDelta(event.getDamage());

        // 吸血按"估算的实际造成伤害"计算
        ctx.estimatedDamage = result;
        double heal = mods.get("heal.attack", 0);
        if (heal > 0) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
        }
        // 反伤：回敬给攻击者自己（这里指被反伤的攻击方），延迟 1 tick 防递归
        double reflectRatio = mods.get("reflect", 0);
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? resolveDamager(byEntity) : null;
        if (reflectRatio > 0 && damager != null && !damager.equals(player)
                && damager instanceof org.bukkit.entity.Damageable damageable) {
            final double reflected = Math.max(1.0, result * Math.min(2.0, reflectRatio));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (damageable.isValid() && !damageable.isDead()) {
                    damageable.damage(reflected, player);
                }
            });
        }
        return result;
    }

    /**
     * 防守侧结算：DAMAGE_REDUCE / DODGE / DAMAGE_CAP / 吸收 / 濒死保留 / 回血。
     *
     * @param incoming 已被进攻侧修正后的伤害
     */
    private double applyDefense(TriggerCtx ctx, EntityDamageEvent event, Player defender, double incoming) {
        EventMods mods = engine.fire(ctx);

        if (mods.flagged(EventMods.Flag.DODGE)) {
            event.setCancelled(true);
            return 0;
        }
        double result = incoming;
        for (Map.Entry<String, Double> e : mods.numbers().entrySet()) {
            if (e.getKey().startsWith("dmg.reduce.")) {
                result *= Math.max(0, 1 - Math.min(1.0, e.getValue()));
            }
        }
        double flat = mods.get("dmg.reduce.flat", 0);
        if (flat > 0) {
            result = Math.max(0, result - flat);
        }
        if (mods.has("dmg.cap")) {
            result = Math.min(result, mods.get("dmg.cap"));
        }

        // 濒死保留：必须在事件里就把伤害钳住。
        // 只靠"事后 1 tick 补血"是无效的 —— Bukkit 在伤害事件之后立刻判定死亡，
        // 迟到的补血救不回已经死亡并掉落物品的玩家。
        if (mods.flagged(EventMods.Flag.SURVIVE)) {
            result = clampLethal(defender, result, mods.get("survive.health", 1.0));
        }
        double heal = mods.get("heal.defend", 0);
        if (heal > 0) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    defender.setHealth(Math.min(defender.getMaxHealth(), defender.getHealth() + heal)));
        }
        return result;
    }

    /**
     * 把「会致死」的伤害钳制到只留下 keepHealth 点生命，使玩家在本次伤害中存活。
     * 保留 1 tick 无敌帧，避免同 tick 内的第二次伤害立刻致死。
     */
    private static double clampLethal(Player defender, double damage, double keepHealth) {
        double keep = Math.max(0.5, keepHealth);
        if (defender.getHealth() - damage < keep) {
            double allowed = Math.max(0, defender.getHealth() - keep);
            defender.setNoDamageTicks(Math.max(defender.getNoDamageTicks(), 1));
            return allowed;
        }
        return damage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        TriggerCtx ctx = ctx(shooter, TriggerCtx.TriggerKind.ON_PROJECTILE_HIT, event);
        ctx.projectile = event.getEntity();
        if (event.getHitEntity() instanceof LivingEntity living) {
            ctx.victim = living;
            ctx.target = living;
        }
        if (event.getHitBlock() != null) {
            ctx.block = event.getHitBlock();
        }
        double distance = shooter.getLocation().distance(event.getEntity().getLocation());
        ctx.distance = distance;
        ctx.state("force", forceOf(event.getEntity()));
        apply(ctx, event, shooter);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        TriggerCtx ctx = ctx(shooter, TriggerCtx.TriggerKind.ON_SHOOT, event);
        ctx.projectile = event.getProjectile();
        ctx.force = event.getForce();
        apply(ctx, event, shooter);
        lastForce.put(shooter.getUniqueId(), event.getForce());

        // ON_BOW_CHARGE：拉弓到一定程度时触发（skybreaker_shot 依赖）
        if (event.getForce() >= 0.9f) {
            TriggerCtx chargeCtx = ctx(shooter, TriggerCtx.TriggerKind.ON_BOW_CHARGE, event);
            chargeCtx.force = event.getForce();
            chargeCtx.projectile = event.getProjectile();
            apply(chargeCtx, event, shooter);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        // 玩家射出的箭已由 EntityShootBowEvent 处理，避免 ON_SHOOT 双触发；这里只接非弓类弹射物
        if (event.getEntity() instanceof org.bukkit.entity.AbstractArrow && shooter instanceof Player) {
            return;
        }
        TriggerCtx ctx = ctx(shooter, TriggerCtx.TriggerKind.ON_SHOOT, event);
        ctx.projectile = event.getEntity();
        apply(ctx, event, shooter);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer != null) {
            TriggerCtx ctx = ctx(killer, TriggerCtx.TriggerKind.ON_KILL, event);
            ctx.victim = dead;
            ctx.target = dead;
            ctx.drops = event.getDrops();
            apply(ctx, event, killer);
        }
        // 玩家的 ON_DEATH 统一由 onPlayerDeath 处理（PlayerDeathEvent 继承自 EntityDeathEvent，
        // 两处都跑会导致灵魂绑定耐久被扣两次、粒子/消息发两遍）
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_DEATH, event);
        ctx.victim = player;
        ctx.items.addAll(allItems(player));
        engine.fire(ctx);

        // 灵魂绑定：只保护"确实带有 soul_bound 附魔"的物品，不做全背包保护
        if (ctx.mods.flagged(EventMods.Flag.SOULBOUND)) {
            int protectedCount = 0;
            for (ItemStack drop : new ArrayList<>(event.getDrops())) {
                if (drop != null && isSoulBound(drop)) {
                    event.getDrops().remove(drop);
                    protectedCount++;
                }
            }
            if (protectedCount > 0) {
                final int count = protectedCount;
                Messages.sendRaw(player, Messages.color("&b灵魂绑定 &7保护了 &f" + count + " &7件物品"));
            }
        }
        engine.clearCooldowns(player.getUniqueId());
    }

    /** 物品是否带 soul_bound 附魔（含其它自定义附魔的排除）。 */
    private boolean isSoulBound(ItemStack item) {
        return ItemTags.read(item).keySet().stream()
                .anyMatch(id -> {
                    com.advancedenchants.model.EnchantDef def = engine.registry().get(id);
                    return def != null && def.id().equals("soul_bound");
                });
    }

    // ==================================================================== 方块 / 工具

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_BLOCK_BREAK, event);
        ctx.block = event.getBlock();
        ctx.items.add(player.getInventory().getItemInMainHand());
        ctx.snapshots.put(player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInMainHand().clone());
        ctx.writeBackHand = true;
        apply(ctx, event, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> drops = new ArrayList<>();
        for (Item item : event.getItems()) {
            drops.add(item.getItemStack());
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_LOOT, event);
        ctx.block = event.getBlock();
        ctx.drops = drops;
        ctx.items.add(player.getInventory().getItemInMainHand());
        engine.fire(ctx);

        // ★ 掉落修改必须回写到世界里的 Item 实体，否则 MULTIPLY_DROP / CONVERT_DROP / AUTO_SMELT 全部是死代码
        writeBackDrops(event, ctx);
    }

    /**
     * 把 ctx.drops 的结果回写到 BlockDropItemEvent 生成的 Item 实体上：
     * - 已有条目：改写数量 / 物品类型（CONVERT_DROP、AUTO_SMELT、MULTIPLY_DROP 改数量）
     * - 新增条目（MULTIPLY_DROP 的额外产出）：在方块位置额外生成掉落
     * - 数量为 0 的条目：移除该 Item 实体
     */
    private void writeBackDrops(BlockDropItemEvent event, TriggerCtx ctx) {
        List<ItemStack> result = ctx.drops;
        if (result == null) {
            return;
        }
        List<Item> entities = new ArrayList<>(event.getItems());
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        int shared = Math.min(entities.size(), result.size());
        for (int i = 0; i < shared; i++) {
            Item entity = entities.get(i);
            ItemStack wanted = result.get(i);
            if (wanted == null || wanted.getType().isAir() || wanted.getAmount() <= 0) {
                entity.remove();
                continue;
            }
            ItemStack current = entity.getItemStack();
            if (current.getType() != wanted.getType() || current.getAmount() != wanted.getAmount()) {
                entity.setItemStack(wanted.clone());
            }
        }
        // 引擎删掉了条目（例如掉落被取消）
        for (int i = shared; i < entities.size(); i++) {
            entities.get(i).remove();
        }
        // 引擎新增了额外掉落
        if (result.size() > entities.size() && event.getBlock().getWorld() != null) {
            for (int i = entities.size(); i < result.size(); i++) {
                ItemStack extra = result.get(i);
                if (extra == null || extra.getType().isAir() || extra.getAmount() <= 0) {
                    continue;
                }
                event.getBlock().getWorld().dropItemNaturally(center, extra.clone());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_BLOCK_PLACE, event);
        ctx.block = event.getBlockPlaced();
        ctx.items.add(event.getItemInHand());
        engine.fire(ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        long now = System.currentTimeMillis();
        Long last = lastMineTick.get(event.getPlayer().getUniqueId());
        if (last != null && now - last < 100L) {
            return; // 节流到 0.1s
        }
        lastMineTick.put(event.getPlayer().getUniqueId(), now);
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_MINE_TICK, event);
        ctx.block = event.getBlock();
        ctx.items.add(event.getPlayer().getInventory().getItemInMainHand());
        engine.fire(ctx);
    }

    // ==================================================================== 移动 / 状态

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // 只按方块粒度触发
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_MOVE, event);
        ctx.items.addAll(movementItems(player));
        engine.fire(ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!onGround.getOrDefault(player.getUniqueId(), false) && player.isOnGround()
                && event.getFrom().getY() < event.getTo().getY()) {
            TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_JUMP, event);
            ctx.items.addAll(movementItems(player));
            engine.fire(ctx);
        }
        onGround.put(player.getUniqueId(), player.isOnGround());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_SNEAK, event);
        ctx.items.addAll(defenseItems(event.getPlayer()));
        engine.fire(ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_SWAP_HAND, event);
        ctx.items.add(event.getPlayer().getInventory().getItem(event.getNewSlot()));
        engine.fire(ctx);
    }

    /**
     * ON_EQUIP：装备变化（穿上 / 脱下）。
     *
     * Bukkit 没有「穿装备」事件，这里用 PlayerInteractEvent + 右键 AIR + 主手是护甲/鞘翅/盾牌
     * 来近似（即玩家手持装备右键穿上的那一刻）。脱下装备无法被监听，属已知限制，已在文档注明。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEquip(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        String name = item.getType().name();
        boolean equippable = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("ELYTRA") || name.equals("SHIELD") || name.equals("TURTLE_HELMET");
        if (!equippable) {
            return;
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_EQUIP, event);
        ctx.items.add(item);
        engine.fire(ctx);
    }

    // ==================================================================== 交互 / 物品

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir() || !ItemTags.hasAny(item)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastInteract.get(player.getUniqueId());
        if (last != null && now - last < 150L) {
            return;
        }
        lastInteract.put(player.getUniqueId(), now);

        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_INTERACT, event);
        ctx.block = event.getClickedBlock();
        if (event.getAction().name().contains("ENTITY")) {
            ctx.target = null;
        }
        ctx.items.add(item);
        ctx.snapshots.put(item, item.clone());
        ctx.writeBackHand = true;
        apply(ctx, event, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_CONSUME, event);
        ctx.items.add(event.getItem());
        engine.fire(ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_FISH, event);
        ctx.caught = event.getCaught();
        ctx.state("state", event.getState().name());
        ctx.items.add(event.getPlayer().getInventory().getItemInMainHand());
        // 咬钩/钓上时，把渔获放进 ctx.drops，MULTIPLY_DROP / CONVERT_DROP 才有作用对象
        if (event.getCaught() instanceof Item caughtEntity) {
            ItemStack caught = caughtEntity.getItemStack();
            List<ItemStack> drops = new ArrayList<>();
            drops.add(caught);
            ctx.drops = drops;
            apply(ctx, event, event.getPlayer());
            // 回写 World 中的渔获实体（类型与数量都可能被 CONVERT_DROP / MULTIPLY_DROP 改过）
            ItemStack wanted = drops.get(0);
            if (wanted == null || wanted.getType().isAir() || wanted.getAmount() <= 0) {
                caughtEntity.remove();
            } else if (!wanted.isSimilar(caught)) {
                caughtEntity.setItemStack(wanted.clone());
            }
            for (int i = 1; i < drops.size(); i++) {
                ItemStack extra = drops.get(i);
                if (extra != null && !extra.getType().isAir() && extra.getAmount() > 0) {
                    event.getPlayer().getWorld().dropItemNaturally(
                            event.getPlayer().getLocation().add(0, 0.5, 0), extra.clone());
                }
            }
            return;
        }
        apply(ctx, event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_XP_GAIN, event);
        ctx.xpAmount = event.getAmount();
        ctx.items.addAll(allItems(event.getPlayer()));
        EventMods mods = engine.fire(ctx);
        double factor = mods.get("xp.mult", 1.0);
        if (factor > 1.0) {
            event.setAmount((int) Math.round(event.getAmount() * factor));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        TriggerCtx ctx = new TriggerCtx(event.getPlayer(), TriggerCtx.TriggerKind.ON_DISABLE, event);
        ctx.items.add(event.getBrokenItem());
        engine.fire(ctx);
    }

    // ==================================================================== 附魔台 / 铁砧 / 磨刀石

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        ItemStack result = event.getResult();
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_ANVIL_COMBINE, event);
        ctx.state("left", left);
        ctx.state("right", right);
        ctx.state("result", result);
        ctx.items.add(left == null ? new ItemStack(Material.AIR) : left);
        engine.fire(ctx);
        ItemStack manipulated = (ItemStack) ctx.state("result.out");
        if (manipulated != null) {
            event.setResult(manipulated);
        }
        // 上限兜底：原版铁砧叠加走 addEnchant(..., ignoreLevelRestriction=true)，
        // 会无视附魔自己的 max-level。这里把结果物品的等级钳回上限后再交回事件。
        ItemStack anvilResult = event.getResult();
        if (anvilResult != null && !anvilResult.getType().isAir()
                && engine.registry().clampLevels(anvilResult)) {
            event.setResult(anvilResult);
        }
    }

    /** ON_ENCHANT：附魔台附魔成功（SCHEMA 定义），不是磨刀石。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(org.bukkit.event.enchantment.EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_ENCHANT, event);
        ctx.state("before", item == null ? null : item.clone());
        ctx.state("added", new java.util.LinkedHashMap<>(event.getEnchantsToAdd()));
        ctx.state("cost", event.getExpLevelCost());
        ctx.items.add(item);
        engine.fire(ctx);
    }

    /** 磨刀石：可剥离自定义附魔（config.yml 的 grindstone.strip-enabled 控制）；不再是 ON_ENCHANT。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        if (!stripEnabled) {
            return;
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_DISABLE, event);
        ctx.state("grindstone", true);
        ctx.items.add(event.getInventory().getItem(0));
        ctx.items.add(event.getInventory().getItem(1));
        engine.fire(ctx);
    }

    // ==================================================================== 周期

    /** PASSIVE：每秒一次，仅对真正带附魔的物品判定。 */
    public void tickPassive() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (engine.registry().size() == 0) {
                return;
            }
            TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.PASSIVE, null);
            ctx.items.addAll(defenseItems(player));
            ctx.items.addAll(movementItems(player));
            ctx.items.add(player.getInventory().getItemInMainHand());
            engine.fire(ctx);
        }
    }

    // ==================================================================== 工具方法

    private final Map<java.util.UUID, Long> lastMineTick = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> lastInteract = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<java.util.UUID, Boolean> onGround = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<java.util.UUID, Float> lastForce = new java.util.concurrent.ConcurrentHashMap<>();

    private TriggerCtx ctx(Player player, TriggerCtx.TriggerKind kind, org.bukkit.event.Event event) {
        TriggerCtx ctx = new TriggerCtx(player, kind, event);
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            ctx.items.add(main);
            ctx.snapshots.put(main, main.clone());
            ctx.writeBackHand = true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            ctx.offhand = off;
        }
        return ctx;
    }

    private List<ItemStack> defenseItems(Player player) {
        List<ItemStack> list = new ArrayList<>(5);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                list.add(armor);
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            list.add(off);
        }
        return list;
    }

    private List<ItemStack> movementItems(Player player) {
        List<ItemStack> list = new ArrayList<>(2);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                list.add(armor);
            }
        }
        return list;
    }

    /** 摔落/滑翔类判定用：全部盔甲（含鞘翅所在的胸甲槽）。 */
    private List<ItemStack> gearItems(Player player) {
        return movementItems(player);
    }

    private List<ItemStack> allItems(Player player) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && ItemTags.hasAny(item)) {
                list.add(item);
            }
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir() && ItemTags.hasAny(armor)) {
                list.add(armor);
            }
        }
        return list;
    }

    /**
     * 通用结算：不涉及伤害事件时使用（挖掘 / 交互 / 钓鱼 / 射箭等）。
     * 伤害事件请走 {@link #applyOffense} 与 {@link #applyDefense}，避免攻防通道混在一起。
     */
    private void apply(TriggerCtx ctx, org.bukkit.event.Event event, Player player) {
        EventMods mods = engine.fire(ctx);

        if (mods.flagged(EventMods.Flag.CANCEL_EVENT) && event instanceof org.bukkit.event.Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
        double heal = mods.get("heal", 0) + mods.get("heal.attack", 0) + mods.get("heal.defend", 0);
        if (heal > 0) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
        }
    }

    /** 攻击者实体解析（玩家 / 弹射物 / 伤害来源）。 */
    private Entity resolveDamager(EntityDamageByEntityEvent event) {
        return resolveAttacker(event);
    }

    /** 伤害来源解析（玩家 / 弹射物射手 / DamageSource）。 */
    private Entity resolveAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        try {
            Entity causing = event.getDamageSource().getCausingEntity();
            if (causing != null) {
                return causing;
            }
        } catch (Throwable ignored) {
            // 旧版本兼容
        }
        return damager;
    }

    /** 读取目标身上仍在有效期内的易伤比例（由 MARK 动作写入 PDC）。 */
    private double activeMark(LivingEntity victim) {
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString("advancedenchants:mark_vuln");
            org.bukkit.NamespacedKey untilKey = org.bukkit.NamespacedKey.fromString("advancedenchants:mark_until");
            if (key == null || untilKey == null) {
                return 0;
            }
            Long until = victim.getPersistentDataContainer().get(untilKey, org.bukkit.persistence.PersistentDataType.LONG);
            if (until == null || until < System.currentTimeMillis()) {
                return 0;
            }
            Double vuln = victim.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
            return vuln == null ? 0 : Math.max(0, Math.min(2.0, vuln));
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 攻速：ATTACK_SPEED 动作会把攻击冷却重置，使下一次攻击立即生效。
     * 仅在冷却超过阈值时重置，避免与「每次攻击都重置」的作弊行为等价。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (!engine.registry().anyUsesAction("ATTACK_SPEED")) {
            return;
        }
        float cooldown = player.getAttackCooldown();
        if (cooldown < 1.0f) {
            return;
        }
        TriggerCtx ctx = new TriggerCtx(player, TriggerCtx.TriggerKind.ON_HIT, event);
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            ctx.items.add(main);
        }
        // 以只读方式探测：不执行动作，避免 ON_HIT 双重结算
        boolean wantsReset = false;
        double threshold = 1.1;
        for (ItemStack item : ctx.items) {
            for (Map.Entry<String, Integer> e : ItemTags.read(item).entrySet()) {
                com.advancedenchants.model.EnchantDef def = engine.registry().get(e.getKey());
                if (def == null) {
                    continue;
                }
                for (com.advancedenchants.model.TriggerDef td : def.at(Trigger.ON_HIT)) {
                    for (com.advancedenchants.model.ActionDef action : td.actions()) {
                        if ("ATTACK_SPEED".equals(action.type())) {
                            wantsReset = true;
                            threshold = action.d("max-cooldown", 1.1);
                        }
                    }
                }
            }
        }
        if (wantsReset && cooldown >= threshold) {
            resetAttackCooldown(player);
        }
    }

    /** 重置攻击冷却（Paper 扩展方法；不可用时退化为无效果）。 */
    private void resetAttackCooldown(Player player) {
        for (java.lang.reflect.Method method : player.getClass().getMethods()) {
            if (method.getName().equals("resetAttackCooldown") && method.getParameterCount() == 0) {
                try {
                    method.invoke(player);
                    return;
                } catch (Throwable ignored) {
                    // 尝试下一个
                }
            }
        }
    }

    private float forceOf(Entity projectile) {
        try {
            if (projectile instanceof org.bukkit.entity.Arrow arrow && arrow.getVelocity().length() > 0) {
                return (float) Math.min(1.0, arrow.getVelocity().length() / 3.0);
            }
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }
}
