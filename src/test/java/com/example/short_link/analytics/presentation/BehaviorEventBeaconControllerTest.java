package com.example.short_link.analytics.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.analytics.application.write.BehaviorContext;
import com.example.short_link.analytics.application.write.BehaviorEventCommand;
import com.example.short_link.analytics.application.write.RecordBehaviorEventsUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = BehaviorEventBeaconController.class)
class BehaviorEventBeaconControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RecordBehaviorEventsUseCase recordBehaviorEvents;

  // sendBeacon/keepalive fetch 는 Content-Type 을 못 실어 text/plain 으로 온다 — 그 모양 그대로 검증한다.
  @Test
  void acceptsTextPlainBatchAndPassesContext() throws Exception {
    mvc.perform(
            post("/api/v1/public/behavior-events")
                .contentType("text/plain;charset=UTF-8")
                .header("User-Agent", "Mozilla/5.0")
                .header("Sec-GPC", "1")
                .content(
                    """
                    {"sessionId":"a1b2c3d4-e5f6","events":[
                      {"name":"read_progress","postId":7,"depthPct":50},
                      {"name":"second_action","postId":7,"targetType":"series","targetId":"s-3"}
                    ]}
                    """))
        .andExpect(status().isAccepted());

    ArgumentCaptor<List<BehaviorEventCommand>> batch = ArgumentCaptor.captor();
    ArgumentCaptor<BehaviorContext> ctx = ArgumentCaptor.forClass(BehaviorContext.class);
    verify(recordBehaviorEvents).execute(any(), batch.capture(), ctx.capture());
    assertThat(batch.getValue()).hasSize(2);
    assertThat(batch.getValue().get(0).name()).isEqualTo("read_progress");
    assertThat(batch.getValue().get(1).targetType()).isEqualTo("series");
    assertThat(ctx.getValue().gpc()).isTrue();
    assertThat(ctx.getValue().userAgent()).isEqualTo("Mozilla/5.0");
  }

  @Test
  void malformedBodyIsSilently202() throws Exception {
    mvc.perform(
            post("/api/v1/public/behavior-events")
                .contentType("text/plain;charset=UTF-8")
                .content("not-json{{{"))
        .andExpect(status().isAccepted());

    verify(recordBehaviorEvents, never()).execute(any(), anyList(), any());
  }

  // 전역 BodySizeFilter 캡(16KB) 아래이면서 엔드포인트 자체 캡(8KB)은 넘는 크기 — 우리 가드를 격리 검증.
  @Test
  void oversizedBodyIsSilently202() throws Exception {
    String big = "x".repeat(BehaviorEventBeaconController.MAX_BODY_BYTES + 1);
    mvc.perform(
            post("/api/v1/public/behavior-events")
                .contentType("text/plain;charset=UTF-8")
                .content(big))
        .andExpect(status().isAccepted());

    verify(recordBehaviorEvents, never()).execute(any(), anyList(), any());
  }
}
