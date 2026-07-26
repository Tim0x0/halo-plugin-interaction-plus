package com.timxs.interactionplus.core.exception;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * 插件统一业务异常，输出 ProblemDetail 风格响应：
 *
 * <pre>{@code
 * {
 *   "type": "about:blank",
 *   "title": "...",
 *   "status": 409,
 *   "detail": "...",
 *   "code": "GRANT_ALREADY_EXISTS"
 * }
 * }</pre>
 */
public class InteractionPlusException extends ErrorResponseException {

    public InteractionPlusException(HttpStatus status, String code, String title, String detail) {
        super(status);
        getBody().setType(URI.create("about:blank"));
        getBody().setTitle(title);
        getBody().setDetail(detail);
        getBody().setProperty("code", code);
    }

    /**
     * 附加扩展属性，例如佩戴保存失败时的 invalidItems 明细。
     */
    public InteractionPlusException withProperty(String name, @Nullable Object value) {
        getBody().setProperty(name, value);
        return this;
    }

    public static InteractionPlusException notFound(String code, String title, String detail) {
        return new InteractionPlusException(HttpStatus.NOT_FOUND, code, title, detail);
    }

    public static InteractionPlusException conflict(String code, String title, String detail) {
        return new InteractionPlusException(HttpStatus.CONFLICT, code, title, detail);
    }

    public static InteractionPlusException badRequest(String code, String title, String detail) {
        return new InteractionPlusException(HttpStatus.BAD_REQUEST, code, title, detail);
    }

    public static InteractionPlusException unprocessable(String code, String title,
        String detail) {
        // RFC 9110 更名：UNPROCESSABLE_ENTITY 在 Spring 6.2+ 废弃，状态码仍为 422
        return new InteractionPlusException(HttpStatus.UNPROCESSABLE_CONTENT, code, title, detail);
    }
}
