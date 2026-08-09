package com.example.short_link.link.stats.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Entry point for click recording on the redirect path. Does one buffer offer and returns — no DB
 * connection, no transaction, so the 302 never waits on click bookkeeping. Assembly and persistence
 * happen in {@link ClickFlusher}.
 *
 * <p>Past the buffer cap (pathological burst during a DB outage) clicks are dropped with a counter
 * so we have evidence it happened rather than queueing without bound.
 */
@Service
@RequiredArgsConstructor
public class ClickRecorder {

  /** Bot name prefix used when the row is a social/messenger preview crawler hit. */
  public static final String PREVIEW_BOT_NAME_PREFIX = "preview:";

  private final ClickBuffer buffer;
  private final MeterRegistry meterRegistry;

  public void record(ClickContext ctx) {
    enqueue(new PendingClick(ctx, null, Instant.now()));
  }

  /**
   * Same as {@link #record} but forces the row to bot=true with a given name. Used for OG/preview
   * crawlers that yauaa doesn't classify as bots — we still want them in click_event so the stats
   * UI can show "social preview" volume separate from real clicks.
   */
  public void recordPreview(ClickContext ctx, String crawlerLabel) {
    enqueue(new PendingClick(ctx, PREVIEW_BOT_NAME_PREFIX + crawlerLabel, Instant.now()));
  }

  private void enqueue(PendingClick click) {
    if (!buffer.offer(click)) {
      meterRegistry.counter("click_recorder", "result", "dropped_overflow").increment();
    }
  }
}
