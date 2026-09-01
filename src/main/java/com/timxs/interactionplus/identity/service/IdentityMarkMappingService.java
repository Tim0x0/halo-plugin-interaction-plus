package com.timxs.interactionplus.identity.service;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.identity.extension.UserIdentityMarkMapping;
import com.timxs.interactionplus.identity.model.IdentityMarkMappingParam;
import com.timxs.interactionplus.identity.model.IdentityMarkMappingView;
import com.timxs.interactionplus.identity.support.PublicIdentityCache;
import com.timxs.interactionplus.core.support.ExtensionQuerySupport;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.core.support.RoleUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 身份标识映射服务。
 *
 * <p>只支持 Halo 原生角色；roleName 唯一；角色被删除后映射保留，
 * 运行时标记失效（roleExists=false），前台不展示。
 */
@Service
@RequiredArgsConstructor
public class IdentityMarkMappingService {

    /** 身份标识默认排序：priority 降序。 */
    private static final Sort MAPPING_SORT = Sort.by(
        Sort.Order.desc("spec.priority"),
        Sort.Order.desc("metadata.creationTimestamp"));

    private final ReactiveExtensionClient client;
    private final PublicIdentityCache publicIdentityCache;

    /**
     * 分页列出映射，并标记角色存在性与角色显示名（当页角色名去重后一次性查询，避免逐条 fetch）。
     */
    public Mono<ListResult<IdentityMarkMappingView>> listMappings(ServerWebExchange exchange) {
        var options = ListOptions.builder()
            .fieldQuery(isNull("metadata.deletionTimestamp"))
            .build();
        return client.listBy(UserIdentityMarkMapping.class, options,
                ListRequestSupport.toPageRequest(exchange, MAPPING_SORT))
            .flatMap(result -> {
                var roleNames = result.getItems().stream()
                    .map(mapping -> mapping.getSpec().getRoleName())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
                return ExtensionQuerySupport.fetchAll(client, Role.class, roleNames)
                    .map(roleMap -> {
                        var views = result.getItems().stream()
                            .map(mapping -> {
                                var roleName = mapping.getSpec().getRoleName();
                                var role = roleMap.get(roleName);
                                return new IdentityMarkMappingView(mapping, role != null,
                                    role != null ? RoleUtils.displayName(role) : roleName);
                            })
                            .toList();
                        return new ListResult<>(result.getPage(), result.getSize(),
                            result.getTotal(), views);
                    });
            });
    }

    public Mono<UserIdentityMarkMapping> createMapping(IdentityMarkMappingParam param) {
        validate(param, true);
        return requireRoleExists(param.getRoleName())
            .then(ensureRoleNotMapped(param.getRoleName()))
            .then(Mono.defer(() -> {
                var mapping = new UserIdentityMarkMapping();
                var metadata = new Metadata();
                metadata.setName(NameGenerator.generate("identity-mark"));
                mapping.setMetadata(metadata);
                var spec = new UserIdentityMarkMapping.Spec();
                spec.setRoleName(param.getRoleName());
                applyParam(spec, param);
                mapping.setSpec(spec);
                return client.create(mapping);
            }))
            // 映射影响全体用户的身份标识，变更后全量失效，避免脏读至 TTL
            .doOnNext(created -> publicIdentityCache.clearAll());
    }

    public Mono<UserIdentityMarkMapping> updateMapping(String name,
        IdentityMarkMappingParam param) {
        validate(param, false);
        return client.fetch(UserIdentityMarkMapping.class, name)
            .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                ErrorCodes.MAPPING_NOT_FOUND, "映射不存在", "身份标识映射不存在或已被删除。")))
            .flatMap(mapping -> {
                // roleName 创建后不可修改，忽略 param.roleName
                applyParam(mapping.getSpec(), param);
                return client.update(mapping);
            })
            .doOnNext(updated -> publicIdentityCache.clearAll());
    }

    public Mono<Void> deleteMapping(String name) {
        return client.fetch(UserIdentityMarkMapping.class, name)
            .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                ErrorCodes.MAPPING_NOT_FOUND, "映射不存在", "身份标识映射不存在或已被删除。")))
            .flatMap(mapping -> client.delete(mapping).then())
            .doOnSuccess(ignored -> publicIdentityCache.clearAll());
    }

    private void applyParam(UserIdentityMarkMapping.Spec spec, IdentityMarkMappingParam param) {
        spec.setDisplayName(param.getDisplayName());
        spec.setDisplayMode(param.getDisplayMode());
        spec.setIcon(param.getIcon());
        spec.setImage(param.getImage());
        spec.setColor(param.getColor());
        spec.setPriority(param.getPriority() == null ? 0 : param.getPriority());
        spec.setEnabled(param.getEnabled() == null || param.getEnabled());
    }

    private Mono<Void> requireRoleExists(String roleName) {
        return RoleUtils.requireExists(client, List.of(roleName));
    }

    private Mono<Void> ensureRoleNotMapped(String roleName) {
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.roleName", roleName),
                isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserIdentityMarkMapping.class, options,
                Sort.by(Sort.Order.asc("metadata.name")))
            .hasElements()
            .flatMap(exists -> exists
                ? Mono.error(InteractionPlusException.conflict(ErrorCodes.VALIDATION_FAILED,
                    "角色已映射", "该角色已存在身份标识映射。"))
                : Mono.empty());
    }

    private void validate(IdentityMarkMappingParam param, boolean requireRole) {
        if (param == null || !StringUtils.hasText(param.getDisplayName())) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "显示名称不能为空。");
        }
        if (requireRole && !StringUtils.hasText(param.getRoleName())) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "角色不能为空。");
        }
        // displayMode 与 color 不在此校验：spec 上分别有 @Schema(enum) 与 @Schema(pattern)，
        // Halo 写入时按 schema 校验并抛 400，服务层不重复。
        // 上面两条留着是因为 schema 查不到：required 只保证键存在，空串照样通过
    }
}
