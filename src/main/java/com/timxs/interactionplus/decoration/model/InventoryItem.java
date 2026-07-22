package com.timxs.interactionplus.decoration.model;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * 我的装饰库存项。基于授予记录，附带资产快照与可用状态。
 */
@Data
public class InventoryItem {

    /** 可用。 */
    public static final String STATUS_AVAILABLE = "available";
    /** 已过期。 */
    public static final String STATUS_EXPIRED = "expired";
    /** 已撤销。 */
    public static final String STATUS_REVOKED = "revoked";
    /** 已停用（资产被停用 / 归档）。 */
    public static final String STATUS_DISABLED = "disabled";

    private String grantName;
    private String assetName;
    private String type;
    private String displayName;
    private String description;
    private UserDecorationAsset.AssetRef asset;
    private UserDecorationAsset.Payload payload;
    private String categoryName;
    private List<String> tagNames;
    private String rarityName;

    /** 状态：available / expired / revoked / disabled。 */
    private String status;

    /** 是否可佩戴。 */
    private boolean available;

    private Instant grantedAt;
    private Instant expiresAt;
}
