package com.timxs.interactionplus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timxs.interactionplus.decoration.service.BootstrapService;
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

    @InjectMocks
    InteractionPlusPlugin plugin;

    @Test
    void startShouldRegisterSchemesAndBootstrap() {
        when(bootstrapService.initializeDefaults()).thenReturn(Mono.empty());

        plugin.start();

        // 7 个自定义资源全部注册
        verify(schemeManager, times(7)).register(any(), any());
        verify(bootstrapService).initializeDefaults();

        plugin.stop();
        // 7 个自定义资源全部注销
        verify(schemeManager, times(7)).unregister(any());
    }
}
