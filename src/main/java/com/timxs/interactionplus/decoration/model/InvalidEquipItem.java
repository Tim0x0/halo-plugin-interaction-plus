package com.timxs.interactionplus.decoration.model;

import lombok.Data;

/**
 * 佩戴失效项明细。
 */
@Data
public class InvalidEquipItem {

    /** 槽位标识。 */
    private String slot;

    /** 装饰资产内部名。 */
    private String assetName;

    /** 失效原因错误码。 */
    private String reason;

    public InvalidEquipItem() {
    }

    public InvalidEquipItem(String slot, String assetName, String reason) {
        this.slot = slot;
        this.assetName = assetName;
        this.reason = reason;
    }
}
