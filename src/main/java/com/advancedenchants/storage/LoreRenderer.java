package com.advancedenchants.storage;

import com.advancedenchants.model.EnchantDef;
import com.advancedenchants.util.Messages;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lore 渲染：把物品上的自定义附魔渲染成 lore 的一段，并保证幂等
 * （重复渲染不叠加、改等级会更新、移除附魔会清掉该行）。
 *
 * 行格式： {@code <tier颜色>显示名 <灰>罗马等级 [&8· 说明首行]}
 */
public final class LoreRenderer {

    private LoreRenderer() {
    }

    /** 某个附魔在 lore 中对应的显示行（纯文本，用于匹配旧行）。 */
    private static String displayPlain(EnchantDef def) {
        return Messages.plain(def.display());
    }

    public static void render(ItemStack item, EnchantDef def) {
        if (item == null || def == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Map<String, Integer> enchants = ItemTags.read(item);
        Integer level = enchants.get(def.id());

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        // 1) 移除该附魔的旧行
        String plain = displayPlain(def);
        lore.removeIf(line -> line != null && Messages.plain(line).contains(plain));

        // 2) 移除纯空行
        lore.removeIf(line -> line == null || Messages.plain(line).isBlank());

        // 3) 若仍存在则插入到其它附魔行之后
        if (level != null && level > 0) {
            int insertAt = lore.size();
            for (int i = lore.size() - 1; i >= 0; i--) {
                if (isCustomEnchantLine(lore.get(i))) {
                    insertAt = i + 1;
                    break;
                }
            }
            lore.add(insertAt, line(def, level));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /** 渲染一行文本（含颜色码）。 */
    public static String line(EnchantDef def, int level) {
        StringBuilder sb = new StringBuilder();
        sb.append(def.tier().color()).append(def.display());
        sb.append(" &7").append(ItemTags.roman(level));
        if (!def.description().isEmpty()) {
            String first = def.description().get(0);
            first = first.replace("{value}", Messages.num(ItemTags.value(def, level)));
            first = first.replace("{level}", String.valueOf(level));
            if (!first.isBlank()) {
                sb.append(" &8· ").append(first);
            }
        }
        return Messages.color(sb.toString());
    }

    /**
     * 判定一行是否为本插件渲染的附魔行：
     * 形如 "&x显示名 &7III ..." —— 以等级罗马数字结尾或后跟 "·"。
     */
    private static boolean isCustomEnchantLine(String loreLine) {
        if (loreLine == null) {
            return false;
        }
        String plain = Messages.plain(loreLine).trim();
        if (plain.isEmpty()) {
            return false;
        }
        return plain.matches("^.+\\s+(?:X|IX|IV|V?I{0,3}|\\d{1,2})(?:\\s+·.*)?$")
                || plain.contains(" · ");
    }

    /** 读取物品上全部附魔并渲染（用于指令刷新手感）。 */
    public static void renderAll(ItemStack item) {
        for (Map.Entry<String, Integer> e : ItemTags.read(item).entrySet()) {
            EnchantDef def = ItemTags.resolver().apply(e.getKey());
            if (def != null) {
                render(item, def);
            }
        }
    }
}
