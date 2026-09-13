package com.example.short_link.analytics.presentation;

import com.example.short_link.analytics.application.write.BehaviorContext;
import com.example.short_link.analytics.application.write.BehaviorEventCommand;
import com.example.short_link.analytics.application.write.RecordBehaviorEventsUseCase;
import com.example.short_link.analytics.presentation.request.BehaviorEventsRequest;
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

/** CORS 사전 요청 없는 sendBeacon/text/plain을 받기 위해 문자열로 파싱한다. 잘못된 본문은 비콘의 사용자 흐름을 막지 않도록 무시한다. */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class BehaviorEventBeaconController {

  static final int MAX_BODY_BYTES = 8 * 1024;

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
}
