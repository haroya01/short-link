package com.example.short_link.link.stats.presentation.sse;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
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
 * Live click stream. Two auth channels share the endpoint because {@code EventSource} can only put
 * credentials in the query string:
 *
 * <ol>
 *   <li><b>{@code ?token=...}</b> — access JWT. Owner + admin path, used everywhere a logged-in
 *       session is watching.
 *   <li><b>{@code ?claimToken=...}</b> — the 16-byte hex token handed back at anonymous link
 *       creation. Lets the landing-page result card watch its own brand-new link in the gap between
 *       "URL shortened" and "user signs up". The token is cleared in {@link LinkEntity#claim} so
 *       this channel auto-closes the moment the link is adopted into an account; subscribers from
 *       that point need the JWT path.
 * </ol>
 *
 * <p>When both are provided JWT wins (a signed-in user shouldn't be downgraded to anonymous-token
 * trust just because the old token is still in the URL). The JWT path runs its token parse before
 * the link lookup so a bad token can't be used as a "does this shortcode exist" probe — the
 * response is 401 either way. Fast-fail uses the servlet response so the global problem-detail
 * handler doesn't wrap our SSE channel into a JSON 500.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class SseClickStreamController {

  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

  private final JwtTokenService jwt;
  private final LinkLookupQueryService lookup;
  private final SseClickStreamRegistry registry;
  private final MeterRegistry meterRegistry;
  private final LinkAccessGuard accessGuard;

  @GetMapping("/{shortCode}/stream")
  public SseEmitter stream(
      @PathVariable ShortCode shortCode,
      @RequestParam(value = "token", required = false) String token,
      @RequestParam(value = "claimToken", required = false) String claimToken,
      HttpServletResponse response) {
    boolean hasToken = token != null && !token.isBlank();
    boolean hasClaim = claimToken != null && !claimToken.isBlank();
    if (!hasToken && !hasClaim) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }

    Long userId = null;
    if (hasToken) {
      try {
        userId = jwt.parseAccessToken(token);
      } catch (RuntimeException e) {
        return failFast(response, HttpStatus.UNAUTHORIZED);
      }
    }

    LinkEntity link =
        lookup
            .findEntity(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));

    if (userId != null) {
      if (!accessGuard.canView(userId, link)) {
        throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
      }
    } else if (!isValidClaimAccess(link, claimToken)) {
      return failFast(response, HttpStatus.UNAUTHORIZED);
    }

    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    boolean accepted = registry.register(link.linkId(), emitter);
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

  private static boolean isValidClaimAccess(LinkEntity link, String presented) {
    if (link.getUserId() != null) return false;
    String stored = link.getClaimToken();
    if (stored == null) return false;
    return constantTimeEquals(stored, presented);
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }

  private static SseEmitter failFast(HttpServletResponse response, HttpStatus status) {
    response.setStatus(status.value());
    SseEmitter rejected = new SseEmitter(0L);
    rejected.complete();
    return rejected;
  }
}
