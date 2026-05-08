package com.example.short_link.link.api.sse;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Owner-only live click stream. Auth is via {@code ?token=...} (query param) since {@code
 * EventSource} can't set custom headers — the access JWT is parsed manually here. We respond with a
 * fast-fail status code via the servlet response (instead of throwing) so the global problem-detail
 * handler doesn't wrap our SSE channel into a JSON 500.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class SseClickStreamController {

  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

  private final JwtTokenService jwt;
  private final LinkRepository linkRepository;
  private final SseClickStreamRegistry registry;
  private final MeterRegistry meterRegistry;

  @GetMapping("/{shortCode}/stream")
  public SseEmitter stream(
      @PathVariable String shortCode,
      @RequestParam("token") String token,
      HttpServletResponse response) {
    Long userId;
    try {
      userId = jwt.parseAccessToken(token);
    } catch (RuntimeException e) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }

    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    boolean accepted = registry.register(link.getId(), emitter);
    if (!accepted) {
      return failFast(response, HttpStatus.TOO_MANY_REQUESTS);
    }
    try {
      emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
    meterRegistry.counter("sse.click_stream.connected").increment();
    return emitter;
  }

  private static SseEmitter failFast(HttpServletResponse response, HttpStatus status) {
    response.setStatus(status.value());
    SseEmitter rejected = new SseEmitter(0L);
    rejected.complete();
    return rejected;
  }
}
