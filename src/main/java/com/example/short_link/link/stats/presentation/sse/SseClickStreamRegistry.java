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

@Slf4j
@Component
@RequiredArgsConstructor
public class SseClickStreamRegistry {

  private static final int MAX_STREAMS_PER_KEY = 16;

  private final Map<LinkId, List<SseEmitter>> emittersByLinkId = new ConcurrentHashMap<>();
  private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();
  private final MeterRegistry meterRegistry;

  public boolean register(LinkId linkId, SseEmitter emitter) {
    return registerInto(emittersByLinkId, linkId, emitter);
  }

  public boolean registerForUser(Long userId, SseEmitter emitter) {
    return registerInto(emittersByUserId, userId, emitter);
  }

  private <K> boolean registerInto(Map<K, List<SseEmitter>> map, K key, SseEmitter emitter) {
    // Share the removal key lock so insertion cannot orphan an emitter in an evicted bucket.
    boolean[] added = {false};
    map.compute(
        key,
        (k, bucket) -> {
          if (bucket == null) bucket = new CopyOnWriteArrayList<>();
          if (bucket.size() < MAX_STREAMS_PER_KEY) {
            bucket.add(emitter);
            added[0] = true;
          }
          return bucket;
        });
    if (!added[0]) {
      meterRegistry.counter("sse.click_stream.rejected", "reason", "limit").increment();
      return false;
    }
    emitter.onCompletion(() -> removeFrom(map, key, emitter));
    emitter.onTimeout(
        () -> {
          emitter.complete();
          removeFrom(map, key, emitter);
        });
    emitter.onError(
        ex -> {
          emitter.completeWithError(ex);
          removeFrom(map, key, emitter);
        });
    meterRegistry.counter("sse.click_stream.opened").increment();
    return true;
  }

  void remove(LinkId linkId, SseEmitter emitter) {
    removeFrom(emittersByLinkId, linkId, emitter);
  }

  private <K> void removeFrom(Map<K, List<SseEmitter>> map, K key, SseEmitter emitter) {
    // Use the same key lock as register() so bucket eviction cannot discard a concurrent addition.
    boolean[] removed = {false};
    map.computeIfPresent(
        key,
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
    List<SseEmitter> linkBucket = emittersByLinkId.get(event.linkId());
    if (linkBucket != null && !linkBucket.isEmpty()) {
      broadcast(
          linkBucket,
          SseEmitter.event()
              .name("click")
              .data(
                  Map.of(
                      "occurredAt", event.occurredAt().toString(),
                      "countryCode", nullToEmpty(event.countryCode()),
                      "deviceClass", nullToEmpty(event.deviceClass()),
                      "channel", nullToEmpty(event.referrerHost()),
                      "bot", event.bot())));
    }

    // Anonymous links have no account stream; shortCode identifies the owner dashboard row.
    if (event.ownerUserId() == null) return;
    List<SseEmitter> ownerBucket = emittersByUserId.get(event.ownerUserId());
    if (ownerBucket == null || ownerBucket.isEmpty()) return;
    broadcast(
        ownerBucket,
        SseEmitter.event()
            .name("click")
            .data(
                Map.of(
                    "shortCode", nullToEmpty(event.shortCode()),
                    "occurredAt", event.occurredAt().toString(),
                    "countryCode", nullToEmpty(event.countryCode()),
                    "deviceClass", nullToEmpty(event.deviceClass()),
                    "channel", nullToEmpty(event.referrerHost()),
                    "bot", event.bot())));
  }

  private void broadcast(List<SseEmitter> bucket, SseEmitter.SseEventBuilder built) {
    int delivered = 0;
    for (SseEmitter emitter : bucket) {
      try {
        emitter.send(built);
        delivered++;
      } catch (IOException | IllegalStateException e) {
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
