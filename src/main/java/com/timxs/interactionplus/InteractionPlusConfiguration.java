package com.timxs.interactionplus;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 插件级 Spring 配置。
 *
 * <p>启用 {@link EnableScheduling}：Halo 插件的独立 ApplicationContext
 * （见 {@code DefaultPluginApplicationContextFactory}）默认只注册基础注解处理器，
 * <b>不包含</b> {@code ScheduledAnnotationBeanPostProcessor}，因此插件内的 {@code @Scheduled}
 * 默认不会生效，必须由本配置显式开启。插件停用时上下文关闭，已注册的定时任务随之自动取消。
 *
 * <p>当前使用方：{@link com.timxs.interactionplus.decoration.service.GrantRetentionService} 的授予记录保留清理。
 *
 * @author Tim0x0
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
public class InteractionPlusConfiguration {
}
