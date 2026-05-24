package com.example.short_link.profile.presentation.visit;

import com.example.short_link.profile.application.visit.ProfileVisitRecorder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous beacon endpoint called by the public /u/&lt;handle&gt; page on each render. Records a
 * row in {@code profile_visit_event} for the owner's stats dashboard. The frontend fires this once
 * per page-mount via {@code navigator.sendBeacon}; the response is intentionally a 204 No Content
 * so the beacon doesn't block paint.
 */
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileVisitController {

  private final ProfileVisitRecorder recorder;

  @PostMapping("/{username}/visit")
  public ResponseEntity<Void> visit(
      @PathVariable String username,
      @RequestParam(value = "src", required = false) String src,
      @RequestParam(value = "utm_source", required = false) String utmSource,
      @RequestParam(value = "utm_medium", required = false) String utmMedium,
      @RequestParam(value = "utm_campaign", required = false) String utmCampaign,
      @RequestParam(value = "utm_term", required = false) String utmTerm,
      @RequestParam(value = "utm_content", required = false) String utmContent,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    recorder.recordUsername(
        username,
        referrer,
        userAgent,
        clientIp(req),
        acceptLanguage,
        src,
        utmSource,
        utmMedium,
        utmCampaign,
        utmTerm,
        utmContent);
    return ResponseEntity.noContent().build();
  }

  private static String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
    return req.getRemoteAddr();
  }
}
