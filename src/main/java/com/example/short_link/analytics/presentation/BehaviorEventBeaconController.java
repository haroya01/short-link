package com.example.short_link.analytics.presentation;

import com.example.short_link.analytics.application.write.BehaviorContext;
import com.example.short_link.analytics.application.write.BehaviorEventCommand;
import com.example.short_link.analytics.application.write.RecordBehaviorEventsUseCase;
import com.example.short_link.common.web.ClientIp;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 행동 이벤트 비콘. 인증 없음, 응답은 항상 202 — 비콘이 UX 를 막거나 콘솔 에러를 만들면 안 된다. 본문은 문자열로 받아 직접 파싱한다: 프론트가
 * Content-Type 없는 keepalive fetch/sendBeacon(text/plain, CORS 사전요청 없음)으로 쏘기 때문. 파싱 실패·초과 크기는 조용히
 * 버린다(드랍 카운트는 유스케이스 미터가 담당).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class BehaviorEventBeaconController {

  /** 이 엔드포인트의 자체 상한 — 전역 BodySizeFilter(16KB)보다 좁다. 정상 배치(≤25건)는 수 KB 다. */
  static final int MAX_BODY_BYTES = 8 * 1024;

  /** 페이로드가 원시타입뿐이라 앱 전역 모듈이 필요 없다 — 주입 대신 로컬 매퍼(웹 슬라이스에 ObjectMapper 빈 없음). */
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .configure(
              com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              false);

  private final RecordBehaviorEventsUseCase recordBehaviorEvents;

  @PostMapping(value = "/behavior-events", consumes = MediaType.ALL_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void record(
      @RequestBody(required = false) String body,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      HttpServletRequest req) {
    if (body == null || body.isBlank() || body.length() > MAX_BODY_BYTES) return;
    BehaviorEventsRequest parsed;
    try {
      parsed = MAPPER.readValue(body, BehaviorEventsRequest.class);
    } catch (Exception e) {
      log.debug("behavior beacon body rejected: {}", e.getMessage());
      return;
    }
    if (parsed == null || parsed.events() == null || parsed.events().isEmpty()) return;
    List<BehaviorEventCommand> batch =
        parsed.events().stream()
            .map(
                e ->
                    e == null
                        ? null
                        : new BehaviorEventCommand(
                            e.name(),
                            e.postId(),
                            e.targetType(),
                            e.targetId(),
                            e.depthPct(),
                            e.dwellMs()))
            .toList();
    recordBehaviorEvents.execute(
        parsed.sessionId(),
        batch,
        new BehaviorContext(userAgent, ClientIp.of(req), "1".equals(req.getHeader("Sec-GPC"))));
  }

  record BehaviorEventsRequest(String sessionId, List<Item> events) {

    record Item(
        String name,
        Long postId,
        String targetType,
        String targetId,
        Integer depthPct,
        Long dwellMs) {}
  }
}
