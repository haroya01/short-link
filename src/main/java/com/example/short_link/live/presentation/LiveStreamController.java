package com.example.short_link.live.presentation;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.common.user.OpenToSuspended;
import com.example.short_link.common.web.ClientIp;
import com.example.short_link.live.application.LiveProperties;
import com.example.short_link.live.application.LiveStreamService;
import com.example.short_link.live.application.LiveSubscriber;
import com.example.short_link.live.presentation.response.LiveStreamTokenResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
public class LiveStreamController {

  private final LiveStreamService live;
  private final LiveProperties props;
  private final MeterRegistry meters;

  @OpenToSuspended
  @PostMapping("/notes/{id}/stream-token")
  public LiveStreamTokenResponse noteToken(
      @AuthenticationPrincipal Long userId, @PathVariable long id, HttpServletRequest req) {
    return token(LiveTopic.note(id), userId, req);
  }

  @GetMapping("/notes/{id}/stream")
  public SseEmitter noteStream(
      @PathVariable long id,
      @RequestParam(value = "streamToken", required = false) String streamToken,
      @RequestParam(value = "since", required = false) String since,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      HttpServletRequest req,
      HttpServletResponse res) {
    return stream(LiveTopic.note(id), streamToken, since, lastEventId, req, res);
  }

  @OpenToSuspended
  @PostMapping("/posts/{id}/comments/stream-token")
  public LiveStreamTokenResponse commentsToken(
      @AuthenticationPrincipal Long userId, @PathVariable long id, HttpServletRequest req) {
    return token(LiveTopic.post(id), userId, req);
  }

  @GetMapping("/posts/{id}/comments/stream")
  public SseEmitter commentsStream(
      @PathVariable long id,
      @RequestParam(value = "streamToken", required = false) String streamToken,
      @RequestParam(value = "since", required = false) String since,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      HttpServletRequest req,
      HttpServletResponse res) {
    return stream(LiveTopic.post(id), streamToken, since, lastEventId, req, res);
  }

  @OpenToSuspended
  @PostMapping("/rooms/{id}/stream-token")
  public LiveStreamTokenResponse roomToken(
      @AuthenticationPrincipal Long userId, @PathVariable long id, HttpServletRequest req) {
    return token(LiveTopic.room(id), userId, req);
  }

  @GetMapping("/rooms/{id}/stream")
  public SseEmitter roomStream(
      @PathVariable long id,
      @RequestParam(value = "streamToken", required = false) String streamToken,
      @RequestParam(value = "since", required = false) String since,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      HttpServletRequest req,
      HttpServletResponse res) {
    return stream(LiveTopic.room(id), streamToken, since, lastEventId, req, res);
  }

  private LiveStreamTokenResponse token(LiveTopic topic, Long userId, HttpServletRequest req) {
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return new LiveStreamTokenResponse(live.issueToken(topic, userId, ClientIp.of(req)));
  }

  private SseEmitter stream(
      LiveTopic topic,
      String streamToken,
      String since,
      String lastEventId,
      HttpServletRequest req,
      HttpServletResponse res) {
    Optional<Long> holder = live.holder(topic, streamToken);
    if (holder.isEmpty()) {
      return refuse(res, HttpStatus.UNAUTHORIZED);
    }
    long userId = holder.get();
    Optional<LiveScope> scope = live.open(topic, userId, ClientIp.of(req));
    if (scope.isEmpty()) {
      return refuse(res, HttpStatus.NOT_FOUND);
    }
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("X-Accel-Buffering", "no");
    SseEmitter emitter = new SseEmitter(props.lifetime().toMillis());
    LiveSubscriber subscriber =
        new LiveSubscriber(userId, topic, scope.get(), new SseLiveChannel(emitter));
    if (!live.join(subscriber)) {
      res.setHeader("Retry-After", String.valueOf(LiveStreamService.RETRY_AFTER_SECONDS));
      return refuse(res, HttpStatus.TOO_MANY_REQUESTS);
    }
    emitter.onCompletion(() -> live.leave(subscriber));
    emitter.onTimeout(
        () -> {
          emitter.complete();
          live.leave(subscriber);
        });
    emitter.onError(
        failure -> {
          emitter.completeWithError(failure);
          live.leave(subscriber);
        });
    live.begin(subscriber, cursor(since != null ? since : lastEventId));
    meters.counter("live.streams.opened", "scope", topic.scope().apiName()).increment();
    return emitter;
  }

  private static Long cursor(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.strip());
    } catch (NumberFormatException unreadable) {
      return null;
    }
  }

  private static SseEmitter refuse(HttpServletResponse res, HttpStatus status) {
    res.setStatus(status.value());
    SseEmitter refused = new SseEmitter(0L);
    refused.complete();
    return refused;
  }
}
