package com.example.short_link.link.stats.presentation.sse;

import com.example.short_link.common.security.ClickStreamTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 계정 단위 클릭 라이브 스트림 — 대시보드의 "첫 클릭이 도착하는 순간"용. 링크별 스트림 ({@link SseClickStreamController})과 같은 문법:
 * EventSource 는 Authorization 헤더를 못 실으므로 POST 로 단명 스트림 토큰을 받아 쿼리로 접속한다. 토큰 스코프는 shortCode 자리에 고정
 * 문자열 {@code me:clicks} 를 써 기존 JWT 인프라를 그대로 재사용한다.
 */
@RestController
@RequestMapping("/api/v1/users/me/clicks")
@RequiredArgsConstructor
public class MyClickStreamController {

  /** 토큰 스코프 — 링크 스트림의 shortCode 자리를 대신하는 계정 채널 식별자. */
  static final String STREAM_SCOPE = "me:clicks";

  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

  private final ClickStreamTokenService tokens;
  private final SseClickStreamRegistry registry;
  private final MeterRegistry meterRegistry;

  @PostMapping("/stream-token")
  public StreamTokenResponse issueStreamToken(@AuthenticationPrincipal Long userId) {
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return new StreamTokenResponse(tokens.createStreamToken(userId, STREAM_SCOPE));
  }

  @GetMapping("/stream")
  public SseEmitter stream(
      @RequestParam(value = "streamToken", required = false) String streamToken,
      HttpServletResponse response) {
    if (streamToken == null || streamToken.isBlank()) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }
    Long userId;
    try {
      userId = tokens.parseStreamToken(streamToken, STREAM_SCOPE);
    } catch (RuntimeException e) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }
    if (userId == null) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }

    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    if (!registry.registerForUser(userId, emitter)) {
      return failFast(response, HttpStatus.TOO_MANY_REQUESTS);
    }
    try {
      emitter.send(SseEmitter.event().name("ready").data(Map.of("ok", true)));
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
    meterRegistry.counter("sse.click_stream.connected", "channel", "me").increment();
    return emitter;
  }

  private static SseEmitter failFast(HttpServletResponse response, HttpStatus status) {
    response.setStatus(status.value());
    SseEmitter emitter = new SseEmitter(0L);
    emitter.complete();
    return emitter;
  }
}
