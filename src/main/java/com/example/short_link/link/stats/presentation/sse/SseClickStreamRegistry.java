package com.example.short_link.link.stats.presentation.sse;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkId;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds all open SSE streams keyed by link id. {@link
 * com.example.short_link.link.stats.application.ClickRecorder} fires {@link ClickRecordedEvent}
 * after every click and we fan out a small JSON payload (no PII) to every emitter watching that
 * link.
 *
 * <p>Listed streams self-clean on completion / timeout / error so the map only ever holds live
 * sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseClickStreamRegistry {

  /** Per-emitter limit so a runaway loop on a single link can't push unbounded messages. */
  private static final int MAX_STREAMS_PER_LINK = 16;

  private final Map<LinkId, List<SseEmitter>> emittersByLinkId = new ConcurrentHashMap<>();
  private final MeterRegistry meterRegistry;

  public boolean register(LinkId linkId, SseEmitter emitter) {
    // compute() runs under the key's bin lock, serializing with remove()'s computeIfPresent. That
    // closes the race where remove() evicts an emptied bucket between this method's limit check and
    // the add — which would otherwise orphan this emitter in a bucket no longer reachable from the
    // map (it would never receive events or get cleaned up).
    boolean[] added = {false};
    emittersByLinkId.compute(
        linkId,
        (k, bucket) -> {
          if (bucket == null) bucket = new CopyOnWriteArrayList<>();
          if (bucket.size() < MAX_STREAMS_PER_LINK) {
            bucket.add(emitter);
            added[0] = true;
          }
          return bucket;
        });
    if (!added[0]) {
      meterRegistry.counter("sse.click_stream.rejected", "reason", "limit").increment();
      return false;
    }
    emitter.onCompletion(() -> remove(linkId, emitter));
    emitter.onTimeout(
        () -> {
          emitter.complete();
          remove(linkId, emitter);
        });
    emitter.onError(
        ex -> {
          emitter.completeWithError(ex);
          remove(linkId, emitter);
        });
    meterRegistry.counter("sse.click_stream.opened").increment();
    return true;
  }

  void remove(LinkId linkId, SseEmitter emitter) {
    // computeIfPresent atomically removes the emitter and, when the bucket empties, evicts the
    // entry
    // (returning null) under the same bin lock register() holds — so a concurrent register() can't
    // be adding to a bucket we're about to drop.
    boolean[] removed = {false};
    emittersByLinkId.computeIfPresent(
        linkId,
        (k, bucket) -> {
          removed[0] = bucket.remove(emitter);
          return bucket.isEmpty() ? null : bucket;
        });
    if (removed[0]) {
      meterRegistry.counter("sse.click_stream.closed").increment();
    }
  }

  public int activeStreams(LinkId linkId) {
    List<SseEmitter> bucket = emittersByLinkId.get(linkId);
    return bucket == null ? 0 : bucket.size();
  }

  @EventListener
  public void onClickRecorded(ClickRecordedEvent event) {
    List<SseEmitter> bucket = emittersByLinkId.get(event.linkId());
    if (bucket == null || bucket.isEmpty()) return;
    SseEmitter.SseEventBuilder built =
        SseEmitter.event()
            .name("click")
            .data(
                Map.of(
                    "occurredAt", event.occurredAt().toString(),
                    "countryCode", nullToEmpty(event.countryCode()),
                    "deviceClass", nullToEmpty(event.deviceClass()),
                    "channel", nullToEmpty(event.channel()),
                    "bot", event.bot()));
    int delivered = 0;
    for (SseEmitter emitter : bucket) {
      try {
        emitter.send(built);
        delivered++;
      } catch (IOException | IllegalStateException e) {
        // best-effort: drop dead emitters silently
        emitter.completeWithError(e);
        bucket.remove(emitter);
      }
    }
    if (delivered > 0) {
      meterRegistry.counter("sse.click_stream.broadcast").increment(delivered);
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
