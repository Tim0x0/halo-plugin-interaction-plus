package com.timxs.interactionplus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timxs.interactionplus.decoration.service.BootstrapService;
import com.timxs.interactionplus.template.extension.CustomTemplate;
import com.timxs.interactionplus.template.service.CustomTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

@ExtendWith(MockitoExtension.class)
class InteractionPlusPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemeManager;

    @Mock
    BootstrapService bootstrapService;

    @Mock
    CustomTemplateService customTemplateService;

    @InjectMocks
    InteractionPlusPlugin plugin;

    @Test
    void startShouldRegisterSchemesAndBootstrap() {
        when(bootstrapService.initializeDefaults()).thenReturn(Mono.empty());
        when(customTemplateService.ensureDefaults()).thenReturn(Mono.empty());

        plugin.start();

        // 7 个带索引的自定义资源走双参重载
        verify(schemeManager, times(7)).register(any(), any());
        // CustomTemplate 按主键直取、不建业务索引，走单参重载
        verify(schemeManager).register(CustomTemplate.class);
        verify(bootstrapService).initializeDefaults();
        verify(customTemplateService).ensureDefaults();

        plugin.stop();
        // 8 个自定义资源全部注销
        verify(schemeManager, times(8)).unregister(any());
    }
}
