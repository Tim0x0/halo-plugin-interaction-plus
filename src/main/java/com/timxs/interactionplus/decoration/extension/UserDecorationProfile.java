package com.timxs.interactionplus.decoration.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 用户装扮档案 / 当前佩戴状态。
 *
 * <p>每个用户最多一条（spec.userName 唯一索引）。各槽位存装饰资产内部名。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationProfile",
    plural = "userdecorationprofiles", singular = "userdecorationprofile")
public class UserDecorationProfile extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserDecorationProfileSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户名")
        private String userName;

        @Schema(description = "头像框资产名")
        private String avatarFrame;

        @Schema(description = "称号资产名")
        private String title;

        @Schema(description = "主勋章资产名")
        private String primaryBadge;

        @Schema(description = "展示勋章资产名列表，最多 8 个")
        private List<String> badgeShowcase;

        @Schema(description = "名片背景资产名")
        private String cardBackground;

        @Schema(description = "昵称样式资产名")
        private String nameStyle;

        @Schema(description = "是否公开展示「我的装扮墙」（公开装饰接口）；为空视为公开")
        private Boolean publicDecorationsVisible;

        @Schema(description = "最后保存时间")
        private Instant updatedAt;

        /**
         * 从全部佩戴槽位（含展示勋章列表）中移除指定资产的引用，有变更时刷新 updatedAt。
         * 撤销清理（service 即时路径）与资产删除级联（Reconciler 兜底路径）共用同一实现，
         * 新增佩戴槽位时只改这里，避免两条清理路径漂移。
         *
         * @return 是否发生变更（调用方据此决定是否写回）
         */
        public boolean removeAsset(String assetName) {
            boolean changed = false;
            if (assetName.equals(avatarFrame)) {
                avatarFrame = null;
                changed = true;
            }
            if (assetName.equals(title)) {
                title = null;
                changed = true;
            }
            if (assetName.equals(primaryBadge)) {
                primaryBadge = null;
                changed = true;
            }
            if (assetName.equals(cardBackground)) {
                cardBackground = null;
                changed = true;
            }
            if (assetName.equals(nameStyle)) {
                nameStyle = null;
                changed = true;
            }
            if (badgeShowcase != null && badgeShowcase.remove(assetName)) {
                changed = true;
            }
            if (changed) {
                updatedAt = Instant.now();
            }
            return changed;
        }
    }
}
