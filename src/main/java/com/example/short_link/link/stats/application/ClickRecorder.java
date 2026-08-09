package com.example.short_link.link.stats.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClickRecorder {

  public static final String PREVIEW_BOT_NAME_PREFIX = "preview:";

  private final ClickBuffer buffer;
  private final MeterRegistry meterRegistry;

  public void record(ClickContext ctx) {
    enqueue(new PendingClick(ctx, null, Instant.now()));
  }

  public void recordPreview(ClickContext ctx, String crawlerLabel) {
    enqueue(new PendingClick(ctx, PREVIEW_BOT_NAME_PREFIX + crawlerLabel, Instant.now()));
  }

  private void enqueue(PendingClick click) {
    if (!buffer.offer(click)) {
      meterRegistry.counter("click_recorder", "result", "dropped_overflow").increment();
    }
  }
}
