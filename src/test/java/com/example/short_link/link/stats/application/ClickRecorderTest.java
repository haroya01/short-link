package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClickRecorderTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private ClickContext ctx() {
    return ClickContext.of(new LinkId(1L), "https://example.com", null, "ua", "1.2.3.4", null);
  }

  @Test
  void recordEnqueuesWithoutForcedBotName() {
    ClickBuffer buffer = new ClickBuffer(new ClickRecorderProperties(10, 500));
    ClickRecorder recorder = new ClickRecorder(buffer, registry);

    recorder.record(ctx());

    List<PendingClick> drained = buffer.drain(10);
    assertThat(drained).hasSize(1);
    assertThat(drained.get(0).forcedBotName()).isNull();
    assertThat(drained.get(0).occurredAt()).isNotNull();
  }

  @Test
  void recordPreviewEnqueuesWithPrefixedLabel() {
    ClickBuffer buffer = new ClickBuffer(new ClickRecorderProperties(10, 500));
    ClickRecorder recorder = new ClickRecorder(buffer, registry);

    recorder.recordPreview(ctx(), "prefetch");

    assertThat(buffer.drain(10).get(0).forcedBotName()).isEqualTo("preview:prefetch");
  }

  @Test
  void overflowDropsClickAndCountsIt() {
    ClickBuffer buffer = new ClickBuffer(new ClickRecorderProperties(1, 500));
    ClickRecorder recorder = new ClickRecorder(buffer, registry);

    recorder.record(ctx());
    recorder.record(ctx());

    assertThat(buffer.size()).isEqualTo(1);
    assertThat(registry.counter("click_recorder", "result", "dropped_overflow").count())
        .isEqualTo(1.0);
  }
}
