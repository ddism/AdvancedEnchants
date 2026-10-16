package com.advancedenchants.command;

import com.advancedenchants.core.EnchantLoader;
import com.advancedenchants.core.EnchantRegistry;
import com.advancedenchants.core.Engine;
import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.model.Tier;
import com.advancedenchants.model.TriggerDef;
import com.advancedenchants.storage.ItemTags;
import com.advancedenchants.storage.LoreRenderer;
import com.advancedenchants.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /ae 指令组 + Tab 补全。 */
public final class EnchantCommands implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EnchantRegistry registry;
    private final Engine engine;
    private final EnchantLoader loader;
    private final ReloadHook reloadHook;

    public interface ReloadHook {
        void reload(CommandSender sender);
    }

    public EnchantCommands(Plugin plugin, EnchantRegistry registry, Engine engine,
                           EnchantLoader loader, ReloadHook reloadHook) {
        this.plugin = plugin;
        this.registry = registry;
        this.engine = engine;
        this.loader = loader;
        this.reloadHook = reloadHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("advancedenchants.admin")) {
                    noPerm(sender);
                    return true;
                }
                reloadHook.reload(sender);
            }
            case "list" -> {
                if (!sender.hasPermission("advancedenchants.admin")) {
                    noPerm(sender);
                    return true;
                }
                list(sender, args.length > 1 ? args[1] : null);
            }
            case "info" -> {
                if (args.length < 2) {
                    Messages.send(sender, "&c用法: /" + label + " info <附魔ID>");
                    return true;
                }
                info(sender, args[1]);
            }
            case "give" -> give(sender, args);
            case "enchant" -> enchant(sender, args);
            case "clear" -> clear(sender, args);
            case "debug" -> {
                if (!sender.hasPermission("advancedenchants.admin")) {
                    noPerm(sender);
                    return true;
                }
                debugState = !debugState;
                engine.setDebug(debugState);
                Messages.send(sender, "&7调试模式: " + (debugState ? "&a开启" : "&c关闭"));
            }
            case "inspect" -> inspect(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private boolean debugState;

    private boolean isDebug() {
        return debugState;
    }

    private void help(CommandSender sender, String label) {
        Messages.send(sender, "&8&m─────&r &bAdvancedEnchants &8&m─────");
        Messages.send(sender, "&f/" + label + " list [等级] &7- 列出附魔（tier: COMMON...）");
        Messages.send(sender, "&f/" + label + " info <ID> &7- 查看附魔定义");
        Messages.send(sender, "&f/" + label + " give <ID> [等级] [玩家] &7- 获得附魔书");
        Messages.send(sender, "&f/" + label + " enchant <ID> [等级] &7- 给手持物品附魔");
        Messages.send(sender, "&f/" + label + " clear &7- 清除手持物品的自定义附魔");
        Messages.send(sender, "&f/" + label + " inspect &7- 查看手持物品的附魔数据");
        Messages.send(sender, "&f/" + label + " reload &7- 重载配置");
        Messages.send(sender, "&f/" + label + " debug &7- 开关调试输出");
        Messages.send(sender, "&7已注册附魔: &f" + registry.size() + " &7条");
    }

    private void list(CommandSender sender, String tierFilter) {
        Map<String, List<String>> byTier = registry.idsByTier();
        Tier only = tierFilter == null ? null : Tier.parse(tierFilter);
        Messages.send(sender, "&8&m─────&r &b附魔列表 &7(" + registry.size() + ") &8&m─────");
        for (Tier tier : Tier.values()) {
            if (only != null && tier != only) {
                continue;
            }
            List<String> ids = byTier.get(tier.name());
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            Messages.send(sender, tier.color() + "【" + tier.zh() + " &7" + ids.size() + tier.color() + "】 &7"
                    + String.join(", ", ids));
        }
    }

    private void info(CommandSender sender, String id) {
        EnchantDef def = registry.get(id);
        if (def == null) {
            Messages.send(sender, "&c未找到附魔: " + id);
            return;
        }
        Messages.send(sender, "&8&m─────&r " + def.tier().color() + def.display() + " &8(&f" + def.id() + "&8)");
        Messages.send(sender, "&7等级: " + def.tier().color() + def.tier().zh() + " &8| &7最大等级: &f" + def.maxLevel()
                + " &8| &7权重: &f" + def.rarityWeight());
        Messages.send(sender, "&7适用: &f" + def.types());
        if (!def.conflicts().isEmpty()) {
            Messages.send(sender, "&7互斥: &f" + String.join(", ", def.conflicts()));
        }
        for (String line : def.description()) {
            Messages.send(sender, "  &8- " + line.replace("{value}",
                    Messages.num(EnchantRegistry.primaryValue(def, 1))));
        }
        for (Map.Entry<com.advancedenchants.model.Trigger, List<TriggerDef>> e : def.triggers().entrySet()) {
            for (TriggerDef td : e.getValue()) {
                Messages.send(sender, "&8» &f" + e.getKey() + " &7概率 &f"
                        + Messages.num(td.chance()) + "% &7冷却 &f" + (td.cooldownMillis() / 1000.0) + "s &7动作 &f"
                        + td.actions().size() + " &7条件 &f" + td.conditions().size());
            }
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancedenchants.admin")) {
            noPerm(sender);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, "&c用法: /ae give <附魔ID> [等级] [玩家]");
            return;
        }
        EnchantDef def = registry.get(args[1]);
        if (def == null) {
            Messages.send(sender, "&c未找到附魔: " + args[1]);
            return;
        }
        int level = args.length > 2 ? parseLevel(args[2], 1) : 1;
        level = Math.max(1, Math.min(def.maxLevel(), level));
        Player target = args.length > 3 ? Bukkit.getPlayerExact(args[3])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            Messages.send(sender, "&c找不到目标玩家");
            return;
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemTags.put(book, def, level);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(book);
        leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        Messages.send(sender, "&a已给予 " + target.getName() + " " + def.display() + " &7Lv" + level);
    }

    private void enchant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "&c该指令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("advancedenchants.use")) {
            noPerm(player);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, "&c用法: /ae enchant <附魔ID> [等级]");
            return;
        }
        EnchantDef def = registry.get(args[1]);
        if (def == null) {
            Messages.send(sender, "&c未找到附魔: " + args[1]);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Messages.send(sender, "&c请手持一件物品");
            return;
        }
        if (!def.supportsAny(com.advancedenchants.model.MaterialTypes.of(item.getType()))) {
            Messages.send(sender, "&c该附魔不适用于 " + item.getType() + "（适用: " + def.types() + "）");
            return;
        }
        int level = Math.max(1, Math.min(def.maxLevel(), args.length > 2 ? parseLevel(args[2], 1) : 1));
        if (!ItemTags.put(item, def, level)) {
            Messages.send(sender, "&c该物品的自定义附魔已达上限（" + ItemTags.maxPerItem() + "）");
            return;
        }
        LoreRenderer.render(item, def);
        Messages.send(sender, "&a已为手持物品附加 " + def.display() + " &7Lv" + level);
    }

    private void clear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "&c该指令只能由玩家执行");
            return;
        }
        if (!player.hasPermission("advancedenchants.use")) {
            noPerm(player);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Messages.send(sender, "&c请手持一件物品");
            return;
        }
        Map<String, Integer> data = ItemTags.read(item);
        ItemTags.clearAll(item);
        // 清理 lore 里残留的附魔行
        for (String id : data.keySet()) {
            EnchantDef def = registry.get(id);
            if (def != null) {
                LoreRenderer.render(item, def);
            }
        }
        Messages.send(sender, "&a已清除 &f" + data.size() + " &a条自定义附魔");
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Messages.send(sender, "&c请手持一件物品");
            return;
        }
        Messages.send(sender, "&7物品: &f" + item.getType() + " &8| &7自定义附魔: &f" + ItemTags.describe(item)
                + " &8| &7灵魂: &f" + ItemTags.souls(item));
    }

    private int parseLevel(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void noPerm(CommandSender sender) {
        Messages.send(sender, "&c你没有权限执行该指令");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("list", "info", "give", "enchant", "clear", "inspect", "reload", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("list")) {
                for (Tier tier : Tier.values()) {
                    out.add(tier.name());
                }
            } else if (sub.equals("info") || sub.equals("give") || sub.equals("enchant")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String id : registry.ids()) {
                    if (id.startsWith(prefix)) {
                        out.add(id);
                    }
                }
            }
            return out;
        }
        if (args.length == 3 && (sub.equals("give") || sub.equals("enchant"))) {
            EnchantDef def = registry.get(args[1]);
            int max = def == null ? 5 : def.maxLevel();
            for (int i = 1; i <= max; i++) {
                out.add(String.valueOf(i));
            }
            return out;
        }
        if (args.length == 4 && sub.equals("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                out.add(player.getName());
            }
        }
        return out;
    }

    /** 供主类写入「最近拉弓力度」用（调试）。 */
    public void setDebugState(boolean value) {
        this.debugState = value;
    }
}
