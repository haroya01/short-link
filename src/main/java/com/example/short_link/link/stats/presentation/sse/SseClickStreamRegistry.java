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
 * com.example.short_link.link.stats.application.ClickFlusher} fires {@link ClickRecordedEvent}
 * after every click and we fan out a small JSON payload (no PII) to every emitter watching that
 * link.
 *
 * <p>계정 채널({@code emittersByUserId})은 대시보드 라이브 모먼트용 — 소유자가 자기 링크 전체의 클릭 도착을 한 스트림으로 받는다. 페이로드에
 * {@code shortCode} 가 실려 어느 행을 깨울지 안다.
 *
 * <p>Listed streams self-clean on completion / timeout / error so the map only ever holds live
 * sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseClickStreamRegistry {

  /** Per-emitter limit so a runaway loop on a single link can't push unbounded messages. */
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
    // compute() runs under the key's bin lock, serializing with remove()'s computeIfPresent. That
    // closes the race where remove() evicts an emptied bucket between this method's limit check and
    // the add — which would otherwise orphan this emitter in a bucket no longer reachable from the
    // map (it would never receive events or get cleaned up).
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
    // computeIfPresent atomically removes the emitter and, when the bucket empties, evicts the
    // entry (returning null) under the same bin lock register() holds — so a concurrent register()
    // can't be adding to a bucket we're about to drop.
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
                      "channel", nullToEmpty(event.channel()),
                      "bot", event.bot())));
    }

    // 계정 채널 — 익명 링크(owner null)는 나가지 않는다. shortCode 로 대시보드 행을 특정한다.
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
                    "channel", nullToEmpty(event.channel()),
                    "bot", event.bot())));
  }

  private void broadcast(List<SseEmitter> bucket, SseEmitter.SseEventBuilder built) {
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
