package com.advancedenchants.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Material -> ApplyType 归类表（版本无关，只依赖 Material 名称后缀/枚举值）。 */
public final class MaterialTypes {

    private static final Map<Material, Set<ApplyType>> CACHE = new HashMap<>();

    private MaterialTypes() {
    }

    public static Set<ApplyType> of(Material material) {
        if (material == null) {
            return Collections.emptySet();
        }
        Set<ApplyType> cached = CACHE.get(material);
        if (cached != null) {
            return cached;
        }
        Set<ApplyType> types = EnumSet.noneOf(ApplyType.class);
        String n = material.name();

        switch (material) {
            case ELYTRA -> types.add(ApplyType.ELYTRA);
            case SHIELD -> types.add(ApplyType.SHIELD);
            case TRIDENT -> types.add(ApplyType.TRIDENT);
            case BOW -> types.add(ApplyType.BOW);
            case CROSSBOW -> types.add(ApplyType.CROSSBOW);
            case FISHING_ROD -> types.add(ApplyType.FISHING_ROD);
            case SHEARS -> types.add(ApplyType.SHEARS);
            default -> {
                if (n.endsWith("_SWORD")) {
                    types.add(ApplyType.SWORD);
                } else if (n.endsWith("_AXE")) {
                    types.add(ApplyType.AXE);
                } else if (n.endsWith("_PICKAXE")) {
                    types.add(ApplyType.PICKAXE);
                } else if (n.endsWith("_SHOVEL")) {
                    types.add(ApplyType.SHOVEL);
                } else if (n.endsWith("_HOE")) {
                    types.add(ApplyType.HOE);
                } else if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                        || n.endsWith("_BOOTS") || n.equals("TURTLE_HELMET")) {
                    types.add(ApplyType.ARMOR);
                }
            }
        }
        if (types.isEmpty()) {
            // 非装备类物品（方块、材料等）只对 ALL 生效
            types = Collections.emptySet();
        }
        CACHE.put(material, types);
        return types;
    }

    /** ARMOR 具体部位判定：是否属于可佩戴的 4 个槽位。 */
    public static boolean isArmor(Material material) {
        return of(material).contains(ApplyType.ARMOR);
    }

    public static boolean isToolOrWeapon(Material material) {
        Set<ApplyType> t = of(material);
        return t.contains(ApplyType.SWORD) || t.contains(ApplyType.AXE) || t.contains(ApplyType.PICKAXE)
                || t.contains(ApplyType.SHOVEL) || t.contains(ApplyType.HOE) || t.contains(ApplyType.SHEARS);
    }
}
