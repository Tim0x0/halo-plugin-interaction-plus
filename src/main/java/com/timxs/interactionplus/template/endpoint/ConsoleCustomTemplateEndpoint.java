package com.timxs.interactionplus.template.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.template.model.CustomTemplateParam;
import com.timxs.interactionplus.template.service.CustomTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console 自定义模板 API。
 *
 * <p>路径前缀：{@code /apis/console.api.interaction-plus.timxs.com/v1alpha1}
 *
 * <p>⚠ 无角色模板 —— 模板 HTML 可含 {@code <script>}，写权限等价于站点 JS 执行权，
 * 只有超级管理员（{@code super-role} 的通配规则）能访问。详见 roleTemplate.yaml。
 *
 * <p>只有列表与按组件保存两个路由：三条记录是固定槽位，不开放创建与删除。
 */
@Component
@RequiredArgsConstructor
public class ConsoleCustomTemplateEndpoint implements CustomEndpoint {

    private final CustomTemplateService customTemplateService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/custom-templates", this::listTemplates)
            .PUT("/custom-templates/{component}", this::saveTemplate)
            .build();
    }

    private Mono<ServerResponse> listTemplates(ServerRequest request) {
        return customTemplateService.listAll()
            .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    private Mono<ServerResponse> saveTemplate(ServerRequest request) {
        var component = request.pathVariable("component");
        return request.bodyToMono(CustomTemplateParam.class)
            .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                ErrorCodes.VALIDATION_FAILED, "请求体为空", "请求体不能为空。")))
            .flatMap(param -> customTemplateService.save(component, param))
            .flatMap(template -> ServerResponse.ok().bodyValue(template));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.CONSOLE_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
