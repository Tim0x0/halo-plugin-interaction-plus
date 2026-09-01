package com.timxs.interactionplus.decoration.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Collection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import run.halo.app.extension.ListOptions;

/**
 * 用户装饰授予记录。
 *
 * <p>同一用户同一装饰允许多条历史；授予按<b>来源并存</b>（后台手动 / 角色快照为一个来源，
 * 各外部插件各为一个来源）：同一来源同时至多一条有效授予，不同来源互不吞并；
 * 用户持有 = 任一来源存在有效授予。有效授予判断：未撤销、未过期（见 {@link #isActiveAt}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationGrant",
    plural = "userdecorationgrants", singular = "userdecorationgrant")
public class UserDecorationGrant extends AbstractExtension {

    /** 授予类型：管理员手动单发/多发。 */
    public static final String GRANT_TYPE_MANUAL = "manual";

    /** 授予类型：按 Halo 角色快照授予。 */
    public static final String GRANT_TYPE_ROLE_SNAPSHOT = "role_snapshot";

    /** 授予类型：外部插件经对外发奖 API 发放。 */
    public static final String GRANT_TYPE_EXTERNAL = "external";

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserDecorationGrantSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "被授予用户名")
        private String userName;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "装饰资产内部名")
        private String assetName;

        @Schema(description = "授予类型：manual/role_snapshot/external")
        private String grantType;

        @Schema(description = "角色快照授予时的来源角色名")
        private String sourceRoleName;

        @Schema(maxLength = 253, description = "来源插件标识（grantType=external 时记录调用方插件名）")
        private String sourcePlugin;

        @Schema(maxLength = 200, description = "授予原因")
        private String reason;

        @Schema(description = "过期时间；为空表示永久有效")
        private Instant expiresAt;

        @Schema(description = "是否已撤销")
        private Boolean revoked;

        @Schema(description = "撤销时间")
        private Instant revokedAt;

        @Schema(description = "撤销操作者用户名")
        private String revokedBy;

        @Schema(maxLength = 200, description = "撤销原因")
        private String revokeReason;

        @Schema(description = "授予操作者用户名")
        private String grantedBy;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "授予时间")
        private Instant grantedAt;
    }

    /**
     * 授予记录的三态：有效 / 已过期 / 已撤销。
     * 判定顺序是领域语义——既撤销又过期记为已撤销；各读出口（Console 列表、UC 库存、
     * 佩戴失效原因、保留期清理）统一经 {@link #stateAt} 判定，避免各处 if 链顺序漂移。
     */
    public enum State {
        ACTIVE, EXPIRED, REVOKED
    }

    /**
     * 该授予记录在指定时刻的状态（不含资产状态判断）。
     */
    public State stateAt(Instant now) {
        if (spec == null || Boolean.TRUE.equals(spec.getRevoked())) {
            return State.REVOKED;
        }
        if (spec.getExpiresAt() != null && !spec.getExpiresAt().isAfter(now)) {
            return State.EXPIRED;
        }
        return State.ACTIVE;
    }

    /**
     * 该授予记录当前是否有效（不含资产状态判断）。
     */
    public boolean isActiveAt(Instant now) {
        return stateAt(now) == State.ACTIVE;
    }

    /**
     * 非 ACTIVE 状态的失效时刻：已撤销取撤销时间（缺失回退授予时间），已过期取过期时间；
     * ACTIVE 返回 null。各「失效时刻」读出口（库存失效排序、保留期清理）统一经此判定，
     * 避免回退规则在各处漂移。
     */
    public @Nullable Instant invalidatedAt(State state) {
        if (spec == null) {
            return null;
        }
        return switch (state) {
            case REVOKED -> spec.getRevokedAt() != null
                ? spec.getRevokedAt() : spec.getGrantedAt();
            case EXPIRED -> spec.getExpiresAt();
            case ACTIVE -> null;
        };
    }

    /**
     * 该用户全部未撤销、未删除授予的索引条件。过期不可索引，
     * 有效性需调用方再经 {@link #isActiveAt} 内存过滤。
     */
    public static ListOptions activeGrantOptions(String userName) {
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    /** 该用户该装饰全部未撤销、未删除授予的索引条件（有效性判定同上）。 */
    public static ListOptions activeGrantOptions(String userName, String assetName) {
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName),
                equal("spec.assetName", assetName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    /** 该装饰全部未撤销、未删除授予的索引条件（级联撤销 / 计数聚合用，有效性判定同上）。 */
    public static ListOptions activeGrantOptionsForAsset(String assetName) {
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.assetName", assetName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    /**
     * 打撤销标记四件套：revoked + revokedAt=now + 操作者 + 原因。
     * 各撤销路径（Console 单撤 / 全部撤销 / 外部撤销 / 资产删除级联 / Reconciler 兜底）统一入口，
     * 撤销语义增改字段时只改这里。operator 允许为 null（外部来源撤销暂无操作者语义）。
     */
    public void markRevoked(String operator, String reason) {
        spec.setRevoked(true);
        spec.setRevokedAt(Instant.now());
        spec.setRevokedBy(operator);
        spec.setRevokeReason(reason);
    }

    /**
     * 多条有效授予聚合的展示有效期：任一永久（expiresAt 为空）视为永久（返回 null），
     * 否则取各条中最晚到期。UC 库存与公开装饰墙等读出口共用，保证多来源并存下展示口径一致。
     * 仅对非空的有效授予集合有意义。
     */
    public static Instant effectiveExpiresAt(Collection<UserDecorationGrant> activeGrants) {
        Instant latest = null;
        for (var grant : activeGrants) {
            var expiresAt = grant.getSpec().getExpiresAt();
            if (expiresAt == null) {
                return null;
            }
            if (latest == null || expiresAt.isAfter(latest)) {
                latest = expiresAt;
            }
        }
        return latest;
    }
}
