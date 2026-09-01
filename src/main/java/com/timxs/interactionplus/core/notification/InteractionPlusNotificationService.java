package com.timxs.interactionplus.core.notification;

import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.core.support.SecurityUtils;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;

/**
 * 插件站内通知服务封装。业务模块只调用本服务方法。
 *
 * <p>实现方式：为目标用户创建对应事件的订阅（幂等），再通过
 * {@link NotificationReasonEmitter} 触发 Reason，由 Halo 通知中心匹配模板发送。
 * 通知失败不影响主业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionPlusNotificationService {

    static final String REASON_DECORATION_GRANTED = "interaction-plus-decoration-granted";
    static final String REASON_DECORATION_REVOKED = "interaction-plus-decoration-revoked";
    static final String REASON_SUBMISSION_APPROVED = "interaction-plus-submission-approved";
    static final String REASON_SUBMISSION_REJECTED = "interaction-plus-submission-rejected";
    static final String REASON_BATCH_GRANT_COMPLETED = "interaction-plus-batch-grant-completed";

    private static final String USER_API_VERSION = "v1alpha1";
    private static final String USER_KIND = "User";

    private final NotificationReasonEmitter reasonEmitter;
    private final ReactiveExtensionClient client;

    /**
     * 用户获得装饰（同一批次多个装饰合并为一条）。reason 为空时不显示授予原因句子。
     */
    public Mono<Void> notifyDecorationsGranted(String userName, List<String> decorationNames,
        String reason) {
        if (!StringUtils.hasText(userName) || decorationNames == null
            || decorationNames.isEmpty()) {
            return Mono.empty();
        }
        String reasonSentence = StringUtils.hasText(reason) ? "授予原因：" + reason + "。" : "";
        return emitToUser(REASON_DECORATION_GRANTED, userName, Map.of(
            "count", String.valueOf(decorationNames.size()),
            "decorations", String.join("、", decorationNames),
            "reasonSentence", reasonSentence));
    }

    /**
     * 用户装饰被撤销。reason 为空时不显示撤销原因句子。
     */
    public Mono<Void> notifyDecorationRevoked(String userName, String decorationName,
        String reason) {
        if (!StringUtils.hasText(userName)) {
            return Mono.empty();
        }
        String reasonSentence = StringUtils.hasText(reason) ? "撤销原因：" + reason + "。" : "";
        return emitToUser(REASON_DECORATION_REVOKED, userName, Map.of(
            "decoration", decorationName,
            "reasonSentence", reasonSentence));
    }

    /**
     * 草稿装饰被启用，通知投稿者。
     */
    public Mono<Void> notifyDraftEnabled(String submitterUserName, String decorationName) {
        if (!StringUtils.hasText(submitterUserName)) {
            return Mono.empty();
        }
        return emitToUser(REASON_SUBMISSION_APPROVED, submitterUserName,
            Map.of("decoration", decorationName));
    }

    /**
     * 草稿装饰被驳回删除，通知投稿者。
     */
    public Mono<Void> notifyDraftRejected(String submitterUserName, String decorationName) {
        if (!StringUtils.hasText(submitterUserName)) {
            return Mono.empty();
        }
        return emitToUser(REASON_SUBMISSION_REJECTED, submitterUserName,
            Map.of("decoration", decorationName));
    }

    /**
     * 批量授予完成，通知操作者。
     */
    public Mono<Void> notifyBatchGrantCompleted(String operatorUserName, int success, int skipped,
        int failed) {
        if (!StringUtils.hasText(operatorUserName)
            || SecurityUtils.isAnonymous(operatorUserName)) {
            return Mono.empty();
        }
        return emitToUser(REASON_BATCH_GRANT_COMPLETED, operatorUserName, Map.of(
            "success", String.valueOf(success),
            "skipped", String.valueOf(skipped),
            "failed", String.valueOf(failed)));
    }

    // ───────────────────────── 内部实现 ─────────────────────────

    /**
     * 为目标用户确保订阅（幂等）并触发事件。事件 subject 为目标用户本身，
     * 以保证只有该用户收到通知。
     */
    private Mono<Void> emitToUser(String reasonType, String userName,
        Map<String, Object> attributes) {
        var reasonSubject = Reason.Subject.builder()
            .apiVersion(USER_API_VERSION)
            .kind(USER_KIND)
            .name(userName)
            .title(userName)
            .build();

        // 先取当前操作者，再确保订阅与发送，避免订阅耗时期间安全上下文变化
        return SecurityUtils.getCurrentUserName()
            .flatMap(operator -> ensureSubscription(reasonType, userName)
                .then(reasonEmitter.emit(reasonType, builder -> builder
                    .subject(reasonSubject)
                    .author(UserIdentity.of(operator))
                    .attributes(attributes))))
            .onErrorResume(error -> {
                // 通知失败不影响主业务
                log.error("发送站内通知失败：reasonType={}, user={}", reasonType, userName, error);
                return Mono.empty();
            });
    }

    /**
     * 确保用户对该事件类型有订阅（subject 为用户本人），用确定性名幂等创建：
     * 已存在则不动，不存在才建一次。
     *
     * <p>规避 Halo {@code NotificationCenter.subscribe} 内部「先退订再重建」语义带来的
     * 多余写操作（批量授予逐用户通知时尤为明显）。是否真正送达仍由用户通知偏好决定，
     * 故此处保证订阅存在不影响用户在个人中心关闭通知。
     */
    private Mono<Void> ensureSubscription(String reasonType, String userName) {
        var name = subscriptionName(reasonType, userName);
        return client.fetch(Subscription.class, name)
            .switchIfEmpty(Mono.defer(() -> createSubscription(name, reasonType, userName)))
            .onErrorResume(error -> {
                // 并发重复创建（已存在）或其他异常都不应阻断本次通知发送。
                log.error("确保通知订阅失败：reasonType={}, user={}", reasonType, userName, error);
                return Mono.empty();
            })
            .then();
    }

    private Mono<Subscription> createSubscription(String name, String reasonType, String userName) {
        var subscription = new Subscription();
        var metadata = new Metadata();
        metadata.setName(name);
        subscription.setMetadata(metadata);

        var spec = new Subscription.Spec();
        spec.setUnsubscribeToken(Subscription.generateUnsubscribeToken());

        var subscriber = new Subscription.Subscriber();
        subscriber.setName(userName);
        spec.setSubscriber(subscriber);

        var interestReason = new Subscription.InterestReason();
        interestReason.setReasonType(reasonType);
        interestReason.setSubject(Subscription.ReasonSubject.builder()
            .apiVersion(USER_API_VERSION)
            .kind(USER_KIND)
            .name(userName)
            .build());
        Subscription.InterestReason.ensureSubjectHasValue(interestReason);
        spec.setReason(interestReason);

        subscription.setSpec(spec);
        return client.create(subscription);
    }

    /** 订阅内部名：reasonType + 用户名稳定 hash，确保确定性且符合 metadata.name 规则。 */
    private String subscriptionName(String reasonType, String userName) {
        return reasonType + "-" + NameGenerator.stableHash(userName);
    }
}
