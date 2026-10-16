package com.advancedenchants.util;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色与文本工具：支持传统色码 {@code &a} 与十六进制 {@code &#RRGGBB}。
 *
 * 刻意不依赖 Bungee ChatColor / Adventure MiniMessage：
 * 只做「& → §」与「&#RRGGBB → §x§R§R§G§G§B§B」的纯文本转换，
 * 这样在 1.20.4 ~ 1.21.x 全版本都无需额外依赖，Paper 客户端仍可正确显示 RGB 颜色。
 */
public final class Messages {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Messages() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher m = HEX.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1).toLowerCase(Locale.ROOT);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00a7').append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return sb.toString().replace('&', '\u00a7');
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    /** 去掉颜色码，便于日志与匹配。 */
    public static String plain(String input) {
        if (input == null) {
            return "";
        }
        return color(input).replaceAll("\u00a7[0-9a-fk-orx]", "");
    }

    /** 数值格式化：去掉无意义的小数尾巴。 */
    public static String num(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static void send(CommandSender sender, String raw) {
        sendRaw(sender, color(raw));
    }

    /**
     * 发送纯文本消息。
     *
     * 1.20.4 的 Bukkit 里 {@code sendMessage(String)} 需要 Bungee 的 BaseComponent，
     * 而 1.21 的 Paper 已提供 Adventure 的原生 String/Component 重载。
     * 为避免编译期绑定到任一版本的签名，这里按运行时可用重载反射调用。
     */
    public static void sendRaw(Object target, String colored) {
        if (target == null) {
            return;
        }
        Class<?> clazz = target.getClass();
        // 优先：Adventure 的 Component 重载
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            if (!method.getName().equals("sendMessage") || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.getName().equals("net.md_5.bungee.api.chat.BaseComponent")) {
                continue;
            }
            try {
                method.invoke(target, colored);
                return;
            } catch (Throwable ignored) {
                // 继续尝试其它重载
            }
        }
        // 退化：Server#broadcastMessage 与日志
        try {
            org.bukkit.Bukkit.getLogger().info(plain(colored));
        } catch (Throwable ignored) {
            // 放弃
        }
    }

    public static String prefix() {
        return color("&8[&bAE&8] &r");
    }
}

