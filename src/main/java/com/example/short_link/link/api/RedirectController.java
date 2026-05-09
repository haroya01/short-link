package com.example.short_link.link.api;

import com.example.short_link.link.application.CachedLink;
import com.example.short_link.link.application.ClickRecorder;
import com.example.short_link.link.application.CustomDomainService;
import com.example.short_link.link.application.GeoIpResolver;
import com.example.short_link.link.application.LinkExpiredException;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.application.LinkPreviewRenderer;
import com.example.short_link.link.application.LinkProtectionService;
import com.example.short_link.link.application.LinkViewLimitExceededException;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.UserAgentClassifier;
import com.example.short_link.link.application.UserAgentInfo;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkLookupService lookup;
  private final ClickRecorder clickRecorder;
  private final LinkPreviewCrawlerDetector crawlerDetector;
  private final LinkPreviewRenderer previewRenderer;
  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;
  private final ShortLinkUrlBuilder urlBuilder;
  private final MeterRegistry meterRegistry;
  private final LinkProtectionService protectionService;
  private final GeoIpResolver geoIpResolver;
  private final CustomDomainService customDomainService;
  private final UserAgentClassifier userAgentClassifier;

  @GetMapping("/{shortCode:[0-9A-Za-z]{3,16}}")
  public ResponseEntity<?> redirect(
      @PathVariable String shortCode,
      @RequestParam(value = "src", required = false) String src,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "error";
    try {
      ResponseEntity<?> response =
          handleRedirect(shortCode, src, referrer, userAgent, acceptLanguage, req);
      outcome = classifyOutcome(response);
      return response;
    } catch (LinkNotFoundException e) {
      outcome = "not_found";
      throw e;
    } catch (LinkExpiredException e) {
      outcome = "expired";
      throw e;
    } catch (LinkViewLimitExceededException e) {
      outcome = "view_limit";
      throw e;
    } finally {
      sample.stop(meterRegistry.timer("redirect.latency", "outcome", outcome));
    }
  }

  private ResponseEntity<?> handleRedirect(
      String shortCode,
      String src,
      String referrer,
      String userAgent,
      String acceptLanguage,
      HttpServletRequest req) {
    CachedLink link = lookup.findActiveLink(shortCode);
    // If the request came in on a custom domain (e.g. go.brand.com), make sure the link belongs
    // to that domain's owner — otherwise we'd be exposing every kurl.me short code on every
    // customer's domain. Default Host (kurl.me, www.kurl.me) skips the check.
    Long customOwner = customDomainService.resolveOwner(req.getHeader("Host"));
    if (customOwner != null && !customOwner.equals(link.userId())) {
      throw new LinkNotFoundException(shortCode);
    }
    String crawlerLabel = crawlerDetector.crawlerName(userAgent);
    if (crawlerLabel != null) {
      meterRegistry.counter("short_link.preview").increment();
      // Persist the preview hit with bot=true + bot_name="preview:slackbot" etc. so per-link stats
      // can split social/messenger previews out of real clicks regardless of yauaa coverage.
      clickRecorder.recordPreview(
          link.linkId(),
          link.originalUrl(),
          referrer,
          userAgent,
          clientIp(req),
          acceptLanguage,
          src,
          crawlerLabel);
      LinkEntity entity = linkRepository.findByShortCode(shortCode).orElse(null);
      if (entity == null) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(link.originalUrl()))
            .header("X-Robots-Tag", "noindex, nofollow")
            .build();
      }
      long clicks = clickEventRepository.countHumanByLinkId(link.linkId());
      String html = previewRenderer.render(entity, urlBuilder.build(shortCode), clicks);
      byte[] body = html.getBytes(StandardCharsets.UTF_8);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
          .contentLength(body.length)
          .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
          .header("X-Robots-Tag", "noindex, nofollow")
          .body(body);
    }
    LinkEntity entity = linkRepository.findByShortCode(shortCode).orElse(null);
    if (entity != null && entity.hasPassword()) {
      return htmlResponse(HttpStatus.OK, passwordPrompt(shortCode, false));
    }
    if (entity != null) {
      enforceViewLimit(entity);
    }
    String clientCountry = geoIpResolver.resolve(clientIp(req)).countryCode();
    if (link.isBlockedFor(clientCountry)) {
      meterRegistry
          .counter("redirect.blocked", "country", clientCountry == null ? "unknown" : clientCountry)
          .increment();
      return htmlResponse(HttpStatus.FORBIDDEN, blockedPage());
    }
    UserAgentInfo ua = userAgentClassifier.classify(userAgent);
    CachedLink.Picked picked = link.pick(clientCountry, normalizeOs(ua.osName()), ua.deviceClass());
    clickRecorder.record(
        link.linkId(),
        picked.url(),
        referrer,
        userAgent,
        clientIp(req),
        acceptLanguage,
        src,
        picked.destinationId());
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(picked.url()))
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=90")
        .header("X-Robots-Tag", "noindex, nofollow")
        .build();
  }

  @PostMapping(
      value = "/{shortCode:[0-9A-Za-z]{3,16}}",
      consumes = "application/x-www-form-urlencoded")
  public ResponseEntity<?> unlock(
      @PathVariable String shortCode,
      @RequestParam("password") String password,
      @RequestParam(value = "src", required = false) String src,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    CachedLink link = lookup.findActiveLink(shortCode);
    LinkEntity entity =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (entity.hasPassword() && !protectionService.checkPassword(entity, password)) {
      return htmlResponse(HttpStatus.UNAUTHORIZED, passwordPrompt(shortCode, true));
    }
    enforceViewLimit(entity);
    String clientCountry = geoIpResolver.resolve(clientIp(req)).countryCode();
    if (link.isBlockedFor(clientCountry)) {
      return htmlResponse(HttpStatus.FORBIDDEN, blockedPage());
    }
    UserAgentInfo ua = userAgentClassifier.classify(userAgent);
    CachedLink.Picked picked = link.pick(clientCountry, normalizeOs(ua.osName()), ua.deviceClass());
    clickRecorder.record(
        link.linkId(),
        picked.url(),
        referrer,
        userAgent,
        clientIp(req),
        acceptLanguage,
        src,
        picked.destinationId());
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(picked.url()))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Robots-Tag", "noindex, nofollow")
        .build();
  }

  private static String normalizeOs(String osName) {
    if (osName == null) return null;
    String lower = osName.toLowerCase();
    if (lower.contains("android")) return "android";
    if (lower.contains("ios")) return "ios";
    if (lower.contains("mac")) return "macos";
    if (lower.contains("windows")) return "windows";
    if (lower.contains("linux")) return "linux";
    return null;
  }

  private static String blockedPage() {
    return "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<title>Not available</title></head>"
        + "<body style=\"font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f8fafc;color:#475569\">"
        + "<div style=\"text-align:center;max-width:360px;padding:40px\">"
        + "<h1 style=\"font-size:18px;margin:0 0 8px;color:#0f172a\">This link isn't available in your region.</h1>"
        + "<p style=\"font-size:13px;margin:0\">If you think this is a mistake, contact the link owner.</p>"
        + "</div></body></html>";
  }

  private static String classifyOutcome(ResponseEntity<?> response) {
    if (response.getStatusCode().is3xxRedirection()) return "redirect";
    if (response.getStatusCode() == HttpStatus.OK) return "preview";
    if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) return "password_required";
    return "other";
  }

  private void enforceViewLimit(LinkEntity entity) {
    if (entity.getMaxViews() == null) return;
    int updated = linkRepository.incrementViewCountIfBelowLimit(entity.getId());
    if (updated == 0) {
      throw new LinkViewLimitExceededException(entity.getShortCode());
    }
  }

  private static ResponseEntity<byte[]> htmlResponse(HttpStatus status, String html) {
    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
        .contentLength(bytes.length)
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(bytes);
  }

  private static String passwordPrompt(String shortCode, boolean failed) {
    String error =
        failed
            ? "<p style=\"color:#b91c1c;text-align:center;font-size:13px;margin:8px 0 0\">Invalid password.</p>"
            : "";
    return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
        + shortCode
        + " · password</title></head>"
        + "<body style=\"font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f8fafc\">"
        + "<form method=\"post\" action=\"/"
        + shortCode
        + "\" style=\"background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:24px;width:320px\">"
        + "<h1 style=\"font-size:14px;color:#475569;margin:0 0 12px\">Password required</h1>"
        + "<input type=\"password\" name=\"password\" autofocus required style=\"width:100%;padding:8px;border:1px solid #e2e8f0;border-radius:6px;font-size:14px;box-sizing:border-box\">"
        + "<button type=\"submit\" style=\"margin-top:8px;width:100%;padding:8px;background:#0f172a;color:#fff;border:0;border-radius:6px;font-size:14px;cursor:pointer\">Unlock</button>"
        + error
        + "</form></body></html>";
  }

  private String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
