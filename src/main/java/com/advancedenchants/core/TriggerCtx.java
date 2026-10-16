package com.advancedenchants.core;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 一次触发器执行的上下文。
 * 只承载数据；事件本身的修改通过 {@link EventMods} 在引擎结束后统一应用。
 */
public final class TriggerCtx {

    public final Player player;
    public final TriggerKind kind;
    public final Event event;
    public final Random random = new Random();

    /** 参与本次判定的物品（主手 / 全部盔甲 / 手持渔竿 等）。 */
    public final List<ItemStack> items = new ArrayList<>(4);

    public LivingEntity victim;
    public Entity target;
    public Block block;
    public Entity projectile;
    public Entity caught;
    public ItemStack offhand;
    public double distance;
    public double damage;
    public double force;
    public int xpAmount;
    public List<ItemStack> drops;

    /** 触发时物品的原始快照（用于事件后回写，避免被 Bukkit 覆盖）。 */
    public final Map<ItemStack, ItemStack> snapshots = new IdentityHashMap<>();

    /** 事件结束后要回写的槽位（玩家主手）。 */
    public boolean writeBackHand;

    /** 本次事件要应用的修改。 */
    public EventMods mods = new EventMods();

    /** 本次「已造成伤害」的估算值，供 LIFESTEAL 等按比例取值。 */
    public double estimatedDamage;

    private final Map<String, Object> state = new LinkedHashMap<>();

    public TriggerCtx(Player player, TriggerKind kind, Event event) {
        this.player = player;
        this.kind = kind;
        this.event = event;
    }

    public Object state(String key) {
        return state.get(key);
    }

    public void state(String key, Object value) {
        state.put(key, value);
    }

    public double number(String key, double def) {
        Object v = state.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public int number(String key, int def) {
        Object v = state.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public boolean isSneaking() {
        return player.isSneaking();
    }

    /** 触发器种类（与 model.Trigger 对应，避免模型层引用 Bukkit）。 */
    public enum TriggerKind {
        ON_HIT, ON_KILL, ON_DAMAGED, ON_DEATH, ON_BLOCK_BREAK, ON_BLOCK_PLACE, ON_MINE_TICK,
        ON_SHOOT, ON_PROJECTILE_HIT, ON_FISH, ON_JUMP, ON_FALL_DAMAGE, ON_MOVE, ON_SNEAK,
        ON_BOW_CHARGE, ON_SHIELD_BLOCK, ON_INTERACT, ON_ANVIL_COMBINE, ON_ENCHANT, ON_EQUIP,
        ON_CONSUME, ON_SWAP_HAND, PASSIVE, ON_LOOT, ON_XP_GAIN, ON_DISABLE;

        public com.advancedenchants.model.Trigger toModel() {
            return com.advancedenchants.model.Trigger.valueOf(name());
        }
    }
}
