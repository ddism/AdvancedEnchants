package com.advancedenchants;

import com.advancedenchants.command.EnchantCommands;
import com.advancedenchants.core.ActionExec;
import com.advancedenchants.core.EnchantLoader;
import com.advancedenchants.core.EnchantRegistry;
import com.advancedenchants.core.Engine;
import com.advancedenchants.listener.TriggerListener;
import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.storage.ItemTags;
import com.advancedenchants.util.Messages;
import com.advancedenchants.util.VersionSafe;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * AdvancedEnchants 主类：配置驱动的 Paper 自定义附魔引擎。
 *
 * 生命周期：
 *   onEnable -> 读 config.yml -> 释放/加载 enchants/*.yml -> 注册自定义附魔 -> 注入各层 -> 注册监听
 *   onDisable -> 取消任务、清理冷却
 */
public final class AdvancedEnchants extends JavaPlugin {

    private EnchantRegistry registry;
    private EnchantLoader loader;
    private Engine engine;
    private TriggerListener listener;
    private ActionExec actionExec;
    private EnchantCommands commands;
    private File enchantFolder;
    private int passiveTaskId = -1;

    @Override
    public void onEnable() {
        VersionSafe.init(this);
        saveDefaultConfig();

        this.registry = new EnchantRegistry();
        this.loader = new EnchantLoader(this);
        this.engine = new Engine(registry);
        this.actionExec = new ActionExec(this);
        this.enchantFolder = new File(getDataFolder(), "enchants");

        ItemTags.init(this, getConfig().getInt("max-enchants-per-item", 12), registry::get);
        engine.setActionExec(actionExec);
        engine.setDebug(getConfig().getBoolean("debug", false));
        applyActionFlags();

        loadEnchants(true);

        this.listener = new TriggerListener(this, engine);
        this.listener.setStripEnabled(getConfig().getBoolean("grindstone.strip-enabled", true));
        getServer().getPluginManager().registerEvents(listener, this);

        this.commands = new EnchantCommands(this, registry, engine, loader, this::reloadAll);
        PluginCommand command = getCommand("advancedenchants");
        if (command != null) {
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        } else {
            getLogger().warning("plugin.yml 缺少 advancedenchants 指令定义");
        }

        long interval = Math.max(10, getConfig().getLong("passive-interval-ticks", 20));
        passiveTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                listener.tickPassive();
            } catch (Throwable t) {
                getLogger().warning("PASSIVE 任务异常: " + t);
            }
        }, interval, interval).getTaskId();

        getLogger().info("AdvancedEnchants 已启用：注册 " + registry.size() + " 条附魔，"
                + (registry.serverRegistration() ? "已注册进服务端 Registry" : "降级为 PDC/Lore 模式"));
    }

    @Override
    public void onDisable() {
        if (passiveTaskId != -1) {
            getServer().getScheduler().cancelTask(passiveTaskId);
            passiveTaskId = -1;
        }
        if (engine != null) {
            engine.clearAllCooldowns();
        }
        getLogger().info("AdvancedEnchants 已卸载");
    }

    // ------------------------------------------------------------------ 加载

    private void loadEnchants(boolean log) {
        registry.clear();
        EnchantLoader.Result result = loader.loadAll(registry, enchantFolder);

        int registered = 0;
        int failed = 0;
        for (EnchantDef def : registry.all()) {
            if (VersionSafe.registerEnchantment(EnchantRegistry.keyOf(def.id()), def.id(), def.maxLevel())) {
                registered++;
            } else {
                failed++;
                registry.markRegistrationFailed(def.id());
            }
        }
        registry.setServerRegistration(failed == 0);

        if (!result.warnings().isEmpty()) {
            for (String warning : result.warnings()) {
                getLogger().warning("[配置] " + warning);
            }
        }
        if (log) {
            StringBuilder tiers = new StringBuilder();
            registry.countByTier().forEach((tier, count) -> tiers.append(tier).append('=').append(count).append(' '));
            getLogger().info("读取附魔 " + registry.size() + " 条（" + tiers.toString().trim() + "），"
                    + "服务端注册成功 " + registered + " 条"
                    + (failed > 0 ? "，失败 " + failed + " 条（这些附魔仍可用，仅无法写入原版附魔字段）" : ""));
        }
    }

    private void applyActionFlags() {
        actionExec.allowTerrainChanges = getConfig().getBoolean("allow-terrain-changes", true);
        actionExec.allowCommands = getConfig().getBoolean("allow-commands", false);
        actionExec.allowScripts = getConfig().getBoolean("allow-scripts", false);
        actionExec.protectBlockContainers = getConfig().getBoolean("protect-block-containers", true);
        if (listener != null) {
            listener.setStripEnabled(getConfig().getBoolean("grindstone.strip-enabled", true));
        }
    }

    private void reloadAll(org.bukkit.command.CommandSender sender) {
        reloadConfig();
        applyActionFlags();
        engine.setDebug(getConfig().getBoolean("debug", false));
        loadEnchants(false);
        Messages.send(sender, "&a配置已重载：&f" + registry.size() + " &a条附魔生效");
        if (!registry.registrationFailed().isEmpty()) {
            Messages.send(sender, "&7未注册进服务端的附魔: &f"
                    + String.join(", ", registry.registrationFailed()));
        }
    }

    // ------------------------------------------------------------------ 对外访问

    public EnchantRegistry registry() {
        return registry;
    }

    public Engine engine() {
        return engine;
    }

    public List<String> enchantIds() {
        return List.copyOf(registry.ids());
    }
}
