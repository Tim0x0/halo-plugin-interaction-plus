package com.timxs.interactionplus.decoration.model;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import lombok.Data;

/**
 * 授予记录列表视图：聚合装饰显示名与类型，避免前端全量拉取资产建映射。
 * 过期 / 撤销状态由服务端统一判定，避免客户端时钟偏差。
 */
@Data
public class GrantView {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REVOKED = "revoked";

    private UserDecorationGrant grant;

    /** 装饰显示名；资产已被物理删除时回退为占位文案。 */
    private String assetDisplayName;

    /** 装饰类型。 */
    private String assetType;

    /** 服务端判定的授予状态：active / expired / revoked。 */
    private String status;

    /** 角色快照来源的角色显示名（界面不展示角色内部名）；非角色快照授予为 null。 */
    private String sourceRoleDisplayName;

    /** 装饰素材引用（列表缩略展示用）；资产已被删除时为 null。 */
    private UserDecorationAsset.AssetRef asset;

    /** 装饰扩展数据（称号 / 昵称样式真实渲染用）；资产已被删除时为 null。 */
    private UserDecorationAsset.Payload payload;

    /** 被授予用户的显示名（界面优先展示，回退用户名）。 */
    private String userDisplayName;

    /** 被授予用户的头像 URL。 */
    private String userAvatar;

    public GrantView() {
    }

    public GrantView(UserDecorationGrant grant, String assetDisplayName, String assetType) {
        this.grant = grant;
        this.assetDisplayName = assetDisplayName;
        this.assetType = assetType;
    }
}
