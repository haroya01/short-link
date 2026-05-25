package com.example.short_link.link.application.helper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Stateless support for the redirect / password-unlock flow — picks the client IP behind
 * X-Forwarded-For, normalizes the OS label, and maps a final response status to the metric outcome
 * label. Lifted out of {@code RedirectController} so the same helpers can serve {@code
 * PasswordUnlockController} without duplicating the logic.
 */
public final class LinkRedirectSupport {

  private LinkRedirectSupport() {}

  public static String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }

  public static String normalizeOs(String osName) {
    if (osName == null) return null;
    String lower = osName.toLowerCase();
    if (lower.contains("android")) return "android";
    if (lower.contains("ios")) return "ios";
    if (lower.contains("mac")) return "macos";
    if (lower.contains("windows")) return "windows";
    if (lower.contains("linux")) return "linux";
    return null;
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
