package com.example.short_link.link.stats.presentation.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.ClickStreamTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class MyClickStreamControllerTest {

  private final ClickStreamTokenService tokens = mock(ClickStreamTokenService.class);
  private final SseClickStreamRegistry registry = mock(SseClickStreamRegistry.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final MyClickStreamController controller =
      new MyClickStreamController(tokens, registry, new SimpleMeterRegistry());

  @Test
  void theAccountStreamTellsProxiesNotToBufferIt() {
    when(tokens.parseStreamToken("stok", MyClickStreamController.STREAM_SCOPE)).thenReturn(7L);
    when(registry.registerForUser(eq(7L), any(SseEmitter.class))).thenReturn(true);

    assertThat(controller.stream("stok", response)).isNotNull();

    verify(response).setHeader("Cache-Control", "no-cache, no-transform");
    verify(response).setHeader("X-Accel-Buffering", "no");
  }

  @Test
  void aStreamWithoutAValidTokenIsRefusedBeforeItOpens() {
    when(tokens.parseStreamToken("bad", MyClickStreamController.STREAM_SCOPE))
        .thenThrow(new IllegalArgumentException("expired"));

    controller.stream("bad", response);
    controller.stream(null, response);

    verify(response, org.mockito.Mockito.times(2)).setStatus(401);
    verify(registry, never()).registerForUser(any(), any());
  }

  @Test
  void aFullAccountStreamIsRefusedWith429() {
    when(tokens.parseStreamToken("stok", MyClickStreamController.STREAM_SCOPE)).thenReturn(7L);
    when(registry.registerForUser(eq(7L), any(SseEmitter.class))).thenReturn(false);

    controller.stream("stok", response);

    verify(response).setStatus(429);
  }
}
