package com.example.short_link.link.presentation.sse;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Iterator;
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
 * com.example.short_link.link.application.ClickRecorder} fires {@link ClickRecordedEvent} after
 * every click and we fan out a small JSON payload (no PII) to every emitter watching that link.
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

  private final Map<Long, List<SseEmitter>> emittersByLinkId = new ConcurrentHashMap<>();
  private final MeterRegistry meterRegistry;

  public boolean register(Long linkId, SseEmitter emitter) {
    List<SseEmitter> bucket =
        emittersByLinkId.computeIfAbsent(linkId, k -> new CopyOnWriteArrayList<>());
    if (bucket.size() >= MAX_STREAMS_PER_LINK) {
      meterRegistry.counter("sse.click_stream.rejected", "reason", "limit").increment();
      return false;
    }
    bucket.add(emitter);
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

  void remove(Long linkId, SseEmitter emitter) {
    List<SseEmitter> bucket = emittersByLinkId.get(linkId);
    if (bucket == null) return;
    bucket.remove(emitter);
    if (bucket.isEmpty()) emittersByLinkId.remove(linkId, bucket);
    meterRegistry.counter("sse.click_stream.closed").increment();
  }

  public int activeStreams(Long linkId) {
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
    Iterator<SseEmitter> it = bucket.iterator();
    while (it.hasNext()) {
      SseEmitter emitter = it.next();
      try {
        emitter.send(built);
      } catch (IOException | IllegalStateException e) {
        // best-effort: drop dead emitters silently
        emitter.completeWithError(e);
        bucket.remove(emitter);
      }
    }
    meterRegistry.counter("sse.click_stream.broadcast").increment(bucket.size());
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
