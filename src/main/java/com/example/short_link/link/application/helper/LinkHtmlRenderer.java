package com.example.short_link.link.application.helper;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Static-HTML pages rendered inline by the redirect / unlock flow — expired, blocked, password
 * prompt. Lifted out of {@code RedirectController} so the controller stops being responsible for
 * presentation copy and the markup is editable in one place. The shapes are deliberately small
 * (system-ui font, inline styles) — these are no-asset interstitial pages served from the redirect
 * hot path, not full SPA routes.
 */
public final class LinkHtmlRenderer {

  private LinkHtmlRenderer() {}

  public static ResponseEntity<byte[]> expiredPageResponse(String message) {
    return htmlResponse(HttpStatus.GONE, expiredPage(message));
  }

  public static ResponseEntity<byte[]> blockedPageResponse() {
    return htmlResponse(HttpStatus.FORBIDDEN, blockedPage());
  }

  public static ResponseEntity<byte[]> passwordPromptResponse(
      HttpStatus status, String shortCode, boolean failed) {
    return htmlResponse(status, passwordPrompt(shortCode, failed));
  }

  static String expiredPage(String message) {
    StringBuilder out = new StringBuilder(message.length() + 16);
    for (int i = 0; i < message.length(); i++) {
      char c = message.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<title>Link no longer available</title></head>"
        + "<body style=\"font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f8fafc;color:#475569\">"
        + "<div style=\"text-align:center;max-width:480px;padding:40px;line-height:1.6\">"
        + "<p style=\"font-size:14px;margin:0;color:#0f172a;white-space:pre-wrap\">"
        + out
        + "</p></div></body></html>";
  }

  static String blockedPage() {
    return "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<title>Not available</title></head>"
        + "<body style=\"font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f8fafc;color:#475569\">"
        + "<div style=\"text-align:center;max-width:360px;padding:40px\">"
        + "<h1 style=\"font-size:18px;margin:0 0 8px;color:#0f172a\">This link isn't available in your region.</h1>"
        + "<p style=\"font-size:13px;margin:0\">If you think this is a mistake, contact the link owner.</p>"
        + "</div></body></html>";
  }

  static String passwordPrompt(String shortCode, boolean failed) {
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

  private static ResponseEntity<byte[]> htmlResponse(HttpStatus status, String html) {
    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
        .contentLength(bytes.length)
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(bytes);
  }
}
