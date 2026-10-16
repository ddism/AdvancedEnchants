package com.advancedenchants.core;

import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.model.Trigger;
import com.advancedenchants.model.TriggerDef;
import com.advancedenchants.storage.ItemTags;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 效果引擎：把「一次事件」翻译成「哪些附魔生效 → 条件过滤 → 动作执行」。
 * 所有入口都保证在主线程调用；内部对每个附魔做冷却与概率判定。
 */
public final class Engine {

    private final EnchantRegistry registry;

    /** 冷却表：key = uuid|enchantId|trigger，value = 下次可用时间戳（毫秒）。 */
    private final Map<String, Long> cooldowns = new HashMap<>();

    /** 动作执行器（由主类在启动时注入）。 */
    private ActionExec actionExec;
    private boolean debug;

    public Engine(EnchantRegistry registry) {
        this.registry = registry;
    }

    public void setActionExec(ActionExec exec) {
        this.actionExec = exec;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public EnchantRegistry registry() {
        return registry;
    }

    /**
     * 触发一次事件。
     *
     * @return 本次事件累积的修改（含伤害倍率等），调用方负责 apply 到 Bukkit 事件
     */
    public EventMods fire(TriggerCtx ctx) {
        EventMods mods = new EventMods();
        ctx.mods = mods;
        if (ctx.player == null || ctx.items.isEmpty()) {
            return mods;
        }
        Trigger trigger = ctx.kind.toModel();
        java.util.Set<Material> seen = java.util.EnumSet.noneOf(Material.class);

        for (ItemStack item : ctx.items) {
            if (item == null || item.getType().isAir() || !seen.add(item.getType())) {
                continue;
            }
            Map<String, Integer> enchants = ItemTags.read(item);
            if (enchants.isEmpty()) {
                continue;
            }
            Set<com.advancedenchants.model.ApplyType> itemTypes =
                    com.advancedenchants.model.MaterialTypes.of(item.getType());

            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                EnchantDef def = registry.get(entry.getKey());
                if (def == null) {
                    continue;
                }
                if (!def.supportsAny(itemTypes)) {
                    continue;
                }
                int level = Math.min(entry.getValue(), def.maxLevel());
                for (TriggerDef td : def.at(trigger)) {
                    if (!passCooldown(ctx, def, td)) {
                        continue;
                    }
                    if (!passChance(ctx, td, level)) {
                        continue;
                    }
                    Conditions.bindItems(ctx.items);
                    if (!passConditions(ctx, td, level)) {
                        continue;
                    }
                    if (actionExec != null) {
                        actionExec.execute(ctx, def, td, level);
                    }
                    markCooldown(ctx, def, td);
                    if (debug) {
                        // 调试输出走控制台，避免与各版本的消息组件 API 差异纠缠
                        org.bukkit.Bukkit.getLogger().info("[AE] " + ctx.player.getName()
                                + " 触发 " + def.id() + " Lv" + level + " (" + trigger + ")");
                    }
                }
            }
        }
        return mods;
    }

    /** 便捷：只判断某玩家物品是否定义了某个触发点（避免构造上下文）。 */
    public boolean hasTrigger(Player player, Trigger trigger) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            Map<String, Integer> enchants = ItemTags.read(item);
            for (String id : enchants.keySet()) {
                EnchantDef def = registry.get(id);
                if (def != null && EnchantRegistry.hasTrigger(def, trigger)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 判定

    private boolean passCooldown(TriggerCtx ctx, EnchantDef def, TriggerDef td) {
        if (!td.hasCooldown()) {
            return true;
        }
        String key = ctx.player.getUniqueId() + "|" + def.id() + "|" + td.raw();
        Long until = cooldowns.get(key);
        return until == null || until <= System.currentTimeMillis();
    }

    private void markCooldown(TriggerCtx ctx, EnchantDef def, TriggerDef td) {
        if (!td.hasCooldown()) {
            return;
        }
        String key = ctx.player.getUniqueId() + "|" + def.id() + "|" + td.raw();
        cooldowns.put(key, System.currentTimeMillis() + td.cooldownMillis());
    }

    private boolean passChance(TriggerCtx ctx, TriggerDef td, int level) {
        double chance = td.effectiveChance(level);
        if (chance >= 100.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return ctx.random.nextDouble() * 100.0 < chance;
    }

    private boolean passConditions(TriggerCtx ctx, TriggerDef td, int level) {
        for (com.advancedenchants.model.CondDef cond : td.conditions()) {
            if (!Conditions.test(ctx, cond, level)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 冷却清理

    public void clearCooldowns(UUID uuid) {
        cooldowns.keySet().removeIf(key -> key.startsWith(uuid.toString()));
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    /** 供 /ae info 展示。 */
    public List<String> describe(String id) {
        EnchantDef def = registry.get(id);
        if (def == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add("&b" + def.id() + " &7(" + def.tier().name() + ") 最大等级 " + def.maxLevel());
        out.add("&7适用: &f" + def.types());
        for (Map.Entry<Trigger, List<TriggerDef>> e : def.triggers().entrySet()) {
            for (TriggerDef td : e.getValue()) {
                out.add("&7- &f" + e.getKey() + " &8chance=" + td.chance()
                        + " cd=" + td.cooldownMillis() + "ms actions=" + td.actions().size());
            }
        }
        return out;
    }
}
