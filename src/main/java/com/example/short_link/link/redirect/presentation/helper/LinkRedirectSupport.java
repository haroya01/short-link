package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.common.web.ClientIp;
import com.example.short_link.link.redirect.application.RedirectVisit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Stateless support for the redirect / password-unlock flow — picks the client IP, normalizes the
 * OS label, and maps a final response status to the metric outcome label. Lifted out of {@code
 * RedirectController} so the same helpers can serve {@code PasswordUnlockController} without
 * duplicating the logic.
 */
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
    if (secPurpose != null && secPurpose.toLowerCase().contains("prefetch")) return true;
    if ("prefetch".equalsIgnoreCase(request.getHeader("Purpose"))) return true;
    return "prefetch".equalsIgnoreCase(request.getHeader("X-moz"));
  }

  public static String clientIp(HttpServletRequest req) {
    return ClientIp.of(req);
  }

  /**
   * The request's {@code Sec-Fetch-Site} value, or null when absent. Only the four values the fetch
   * metadata spec defines are kept — anything else is a forged or garbage header and is dropped
   * rather than stored. Read here (not as a {@code @RequestHeader}) so both the redirect flow and
   * the preview branch get it the same way {@code Sec-GPC} is read.
   */
  public static String fetchSite(HttpServletRequest req) {
    String raw = req.getHeader("Sec-Fetch-Site");
    if (raw == null) return null;
    String value = raw.trim().toLowerCase();
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
