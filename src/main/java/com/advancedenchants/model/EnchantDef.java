package com.advancedenchants.model;

import java.util.List;
import java.util.Map;

/**
 * 一个附魔的完整定义（不可变）。
 *
 * @param id            YAML 中的附魔 ID（小写）
 * @param display       显示名（含颜色码）
 * @param tier          稀有度
 * @param maxLevel      最大等级
 * @param types         适用类型
 * @param description   描述行（可为空）
 * @param conflicts     互斥附魔 ID
 * @param rarityWeight  随机权重（未配置时取 tier 默认值）
 * @param triggers      触发器 -> 定义 的有序映射
 */
public record EnchantDef(
        String id,
        String display,
        Tier tier,
        int maxLevel,
        java.util.Set<ApplyType> types,
        List<String> description,
        List<String> conflicts,
        int rarityWeight,
        Map<Trigger, List<TriggerDef>> triggers
) {

    public List<TriggerDef> at(Trigger trigger) {
        List<TriggerDef> list = triggers.get(trigger);
        return list == null ? List.of() : list;
    }

    public boolean supports(ApplyType type) {
        return types.contains(ApplyType.ALL) || types.contains(type);
    }

    public boolean supportsAny(java.util.Set<ApplyType> itemTypes) {
        if (types.contains(ApplyType.ALL)) {
            return true;
        }
        for (ApplyType t : itemTypes) {
            if (types.contains(t)) {
                return true;
            }
        }
        return false;
    }

    public String namespacedId() {
        return "advancedenchants:" + id;
    }
}
