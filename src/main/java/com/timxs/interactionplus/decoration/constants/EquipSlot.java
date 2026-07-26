package com.timxs.interactionplus.decoration.constants;

import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import org.jspecify.annotations.Nullable;

/**
 * 佩戴槽位。
 *
 * <p>勋章支持多佩戴：主勋章 1 个 + 展示勋章最多
 * {@link InteractionPlusConst#BADGE_SHOWCASE_MAX} 个；其余类型均为单选佩戴。
 */
public enum EquipSlot {

    PRIMARY_BADGE("primary_badge", DecorationType.BADGE, 1),
    BADGE_SHOWCASE("badge_showcase", DecorationType.BADGE,
        InteractionPlusConst.BADGE_SHOWCASE_MAX),
    AVATAR_FRAME("avatar_frame", DecorationType.AVATAR_FRAME, 1),
    TITLE("title", DecorationType.TITLE, 1),
    CARD_BACKGROUND("card_background", DecorationType.CARD_BACKGROUND, 1),
    NAME_STYLE("name_style", DecorationType.NAME_STYLE, 1);

    private final String value;
    private final DecorationType assetType;
    private final int maxCount;

    EquipSlot(String value, DecorationType assetType, int maxCount) {
        this.value = value;
        this.assetType = assetType;
        this.maxCount = maxCount;
    }

    public String getValue() {
        return value;
    }

    /** 槽位允许的装饰类型。 */
    public DecorationType getAssetType() {
        return assetType;
    }

    /** 槽位最大佩戴数量。 */
    public int getMaxCount() {
        return maxCount;
    }

    @Nullable
    public static EquipSlot from(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (EquipSlot slot : values()) {
            if (slot.value.equals(value)) {
                return slot;
            }
        }
        return null;
    }
}
