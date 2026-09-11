package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.common.web.ClientIp;
import com.example.short_link.link.redirect.application.RedirectVisit;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class LinkRedirectSupport {

  private LinkRedirectSupport() {}

  public static RedirectVisit visit(
      String referrer,
      String userAgent,
      String acceptLanguage,
      String src,
      Long postId,
      HttpServletRequest request) {
    return new RedirectVisit(
        referrer,
        userAgent,
        clientIp(request),
        acceptLanguage,
        src,
        postId,
        "1".equals(request.getHeader("Sec-GPC")),
        fetchSite(request),
        isPrefetch(request));
  }

  private static boolean isPrefetch(HttpServletRequest request) {
    String secPurpose = request.getHeader("Sec-Purpose");
    if (secPurpose != null && secPurpose.toLowerCase(Locale.ROOT).contains("prefetch")) return true;
    if ("prefetch".equalsIgnoreCase(request.getHeader("Purpose"))) return true;
    return "prefetch".equalsIgnoreCase(request.getHeader("X-moz"));
  }

  public static String clientIp(HttpServletRequest req) {
    return ClientIp.of(req);
  }

  /** Sec-Fetch-Site의 표준 값 네 가지만 저장하며, 누락·알 수 없는 값은 null이다. */
  public static String fetchSite(HttpServletRequest req) {
    String raw = req.getHeader("Sec-Fetch-Site");
    if (raw == null) return null;
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "none", "cross-site", "same-site", "same-origin" -> value;
      default -> null;
    };
  }

  public static String classifyOutcome(ResponseEntity<?> response) {
    if (response.getStatusCode().is3xxRedirection()) return "redirect";
    if (response.getStatusCode() == HttpStatus.OK) return "preview";
    if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) return "password_required";
    if (response.getStatusCode() == HttpStatus.FORBIDDEN) return "blocked";
    if (response.getStatusCode() == HttpStatus.GONE) return "expired";
    return "other";
  }
}
