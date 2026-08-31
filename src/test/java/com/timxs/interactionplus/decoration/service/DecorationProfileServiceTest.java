package com.timxs.interactionplus.decoration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.timxs.interactionplus.decoration.constants.DecorationStatus;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.model.InventoryItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;

class DecorationProfileServiceTest {

    private static final String ASSET_NAME = "asset-badge";
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    @Test
    void shouldBecomeAvailableWhenSameDecorationIsGrantedAgainAfterRevocation() {
        var revoked = grant("grant-old",
            "2026-08-01T00:00:00Z", null, "2026-08-20T00:00:00Z");
        var reissued = grant("grant-new",
            "2026-08-21T00:00:00Z", null, null);

        var item = aggregate(List.of(reissued, revoked), DecorationStatus.ACTIVE);

        assertThat(item.isAvailable()).isTrue();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_AVAILABLE);
        assertThat(item.getGrantName()).isEqualTo("grant-new");
        assertThat(item.getGrantedAt()).isEqualTo(reissued.getSpec().getGrantedAt());
        assertThat(item.getExpiresAt()).isNull();
    }

    @Test
    void shouldStayAvailableWhileAnyGrantSourceIsActive() {
        var expired = grant("grant-expired",
            "2026-08-20T00:00:00Z", "2026-08-25T00:00:00Z", null);
        var revoked = grant("grant-revoked",
            "2026-08-19T00:00:00Z", null, "2026-08-28T00:00:00Z");
        var active = grant("grant-active",
            "2026-08-01T00:00:00Z", "2026-09-30T00:00:00Z", null);

        var item = aggregate(List.of(expired, revoked, active), DecorationStatus.ACTIVE);

        assertThat(item.isAvailable()).isTrue();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_AVAILABLE);
        assertThat(item.getGrantName()).isEqualTo("grant-active");
        assertThat(item.getExpiresAt()).isEqualTo(active.getSpec().getExpiresAt());
    }

    @Test
    void shouldUseRevocationWhenItIsTheMostRecentInvalidationEvent() {
        // 后创建的授予先过期，较早创建的授予后撤销：不能按 grantedAt 误判为过期。
        var newerExpired = grant("grant-newer-expired",
            "2026-08-20T00:00:00Z", "2026-08-21T00:00:00Z", null);
        var olderRevokedLater = grant("grant-older-revoked",
            "2026-08-01T00:00:00Z", null, "2026-08-28T00:00:00Z");

        var item = aggregate(List.of(newerExpired, olderRevokedLater),
            DecorationStatus.ACTIVE);

        assertThat(item.isAvailable()).isFalse();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_REVOKED);
        assertThat(item.getGrantName()).isEqualTo("grant-older-revoked");
    }

    @Test
    void shouldUseExpirationWhenItIsTheMostRecentInvalidationEvent() {
        var newerRevokedEarlier = grant("grant-newer-revoked",
            "2026-08-20T00:00:00Z", null, "2026-08-22T00:00:00Z");
        var olderExpiredLater = grant("grant-older-expired",
            "2026-08-01T00:00:00Z", "2026-08-28T00:00:00Z", null);

        var item = aggregate(List.of(newerRevokedEarlier, olderExpiredLater),
            DecorationStatus.ACTIVE);

        assertThat(item.isAvailable()).isFalse();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_EXPIRED);
        assertThat(item.getGrantName()).isEqualTo("grant-older-expired");
        assertThat(item.getExpiresAt()).isEqualTo(olderExpiredLater.getSpec().getExpiresAt());
    }

    @Test
    void shouldReportAssetAsDisabledWhenAValidGrantStillExists() {
        var active = grant("grant-active",
            "2026-08-01T00:00:00Z", null, null);
        var expiredHistory = grant("grant-expired-history",
            "2026-08-20T00:00:00Z", "2026-08-21T00:00:00Z", null);

        var item = aggregate(List.of(expiredHistory, active), DecorationStatus.DISABLED);

        assertThat(item.isAvailable()).isFalse();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_DISABLED);
        assertThat(item.getGrantName()).isEqualTo("grant-active");
        assertThat(item.getGrantedAt()).isEqualTo(active.getSpec().getGrantedAt());
    }

    @Test
    void shouldKeepTerminalGrantStateWhenAssetIsAlsoDisabled() {
        var expired = grant("grant-expired",
            "2026-08-01T00:00:00Z", "2026-08-28T00:00:00Z", null);

        var item = aggregate(List.of(expired), DecorationStatus.DISABLED);

        assertThat(item.isAvailable()).isFalse();
        assertThat(item.getStatus()).isEqualTo(InventoryItem.STATUS_EXPIRED);
    }

    private static InventoryItem aggregate(List<UserDecorationGrant> grants,
        DecorationStatus assetStatus) {
        return DecorationProfileService.aggregateItem(ASSET_NAME, grants,
            Map.of(ASSET_NAME, asset(assetStatus)), NOW);
    }

    private static UserDecorationAsset asset(DecorationStatus status) {
        var metadata = new Metadata();
        metadata.setName(ASSET_NAME);

        var spec = new UserDecorationAsset.Spec();
        spec.setType("badge");
        spec.setDisplayName("测试勋章");
        spec.setStatus(status.getValue());

        var asset = new UserDecorationAsset();
        asset.setMetadata(metadata);
        asset.setSpec(spec);
        return asset;
    }

    private static UserDecorationGrant grant(String name, String grantedAt, String expiresAt,
        String revokedAt) {
        var metadata = new Metadata();
        metadata.setName(name);

        var spec = new UserDecorationGrant.Spec();
        spec.setUserName("test-user");
        spec.setAssetName(ASSET_NAME);
        spec.setGrantType(UserDecorationGrant.GRANT_TYPE_MANUAL);
        spec.setGrantedAt(Instant.parse(grantedAt));
        spec.setExpiresAt(expiresAt == null ? null : Instant.parse(expiresAt));
        spec.setRevoked(revokedAt != null);
        spec.setRevokedAt(revokedAt == null ? null : Instant.parse(revokedAt));

        var grant = new UserDecorationGrant();
        grant.setMetadata(metadata);
        grant.setSpec(spec);
        return grant;
    }
}
