package com.example.short_link.link.redirect.application.helper;

import com.example.short_link.link.domain.ShortCode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Static-HTML interstitials rendered inline by the redirect / unlock flow — expired, blocked,
 * password prompt. No external assets (served from the redirect hot path), but responsive and
 * on-brand: a centered card, kurl green accent, dark-mode aware, 16px inputs (no iOS zoom). The
 * password prompt optionally renders a Cloudflare Turnstile widget when a site key is configured.
 */
public final class LinkHtmlRenderer {

  private LinkHtmlRenderer() {}

  private static final String STYLE =
      """
      *{box-sizing:border-box}
      :root{--bg:#f8fafc;--card:#fff;--ink:#0f172a;--muted:#64748b;--border:#e2e8f0;\
      --brand:#059669;--press:#047857;--danger:#dc2626}
      @media(prefers-color-scheme:dark){:root{--bg:#000;--card:#0f172a;--ink:#f1f5f9;\
      --muted:#94a3b8;--border:#1e293b}}
      body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;\
      font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;\
      background:var(--bg);color:var(--ink);-webkit-font-smoothing:antialiased}
      .card{width:100%;max-width:360px;background:var(--card);border:1px solid var(--border);\
      border-radius:16px;padding:28px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 10px 30px rgba(15,23,42,.05)}
      .mark{display:flex;gap:3px;align-items:center;margin-bottom:18px}
      .mark i{height:4px;border-radius:2px;background:var(--brand);display:block}
      .mark i:nth-child(1){width:18px}.mark i:nth-child(2){width:12px}.mark i:nth-child(3){width:7px}
      h1{font-size:17px;margin:0 0 6px;letter-spacing:-.01em;line-height:1.4}
      p{font-size:14px;line-height:1.6;color:var(--muted);margin:0;white-space:pre-wrap}
      form{margin-top:18px}
      label{display:block;font-size:13px;color:var(--muted);margin:0 0 8px}
      input{width:100%;padding:12px 14px;border:1px solid var(--border);border-radius:10px;\
      font-size:16px;background:transparent;color:var(--ink);outline:none;transition:border-color .15s,box-shadow .15s}
      input:focus{border-color:var(--brand);box-shadow:0 0 0 3px rgba(5,150,105,.15)}
      button{margin-top:14px;width:100%;padding:13px;background:var(--brand);color:#fff;border:0;\
      border-radius:10px;font-size:15px;font-weight:600;cursor:pointer;transition:background .15s}
      button:hover{background:var(--press)}
      .cf{margin-top:14px;display:flex;justify-content:center}
      .err{color:var(--danger);font-size:13px;margin:10px 0 0;text-align:center}
      """;

  private static String page(String title, String inner) {
    return "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>"
        + title
        + "</title><style>"
        + STYLE
        + "</style></head><body><main class=\"card\">"
        + "<div class=\"mark\"><i></i><i></i><i></i></div>"
        + inner
        + "</main></body></html>";
  }

  public static ResponseEntity<byte[]> expiredPageResponse(String message) {
    return htmlResponse(HttpStatus.GONE, expiredPage(message));
  }

  public static ResponseEntity<byte[]> blockedPageResponse() {
    return htmlResponse(HttpStatus.FORBIDDEN, blockedPage());
  }

  public static ResponseEntity<byte[]> passwordPromptResponse(
      HttpStatus status, ShortCode shortCode, boolean failed, String turnstileSiteKey) {
    return htmlResponse(status, passwordPrompt(shortCode, failed, turnstileSiteKey));
  }

  static String expiredPage(String message) {
    return page(
        "Link no longer available", "<h1>이 링크는 더 이상 열 수 없어요</h1><p>" + escape(message) + "</p>");
  }

  static String blockedPage() {
    return page(
        "Not available", "<h1>이 지역에서는 열 수 없는 링크예요</h1>" + "<p>잘못된 것 같다면 링크를 만든 사람에게 문의하세요.</p>");
  }

  static String passwordPrompt(ShortCode shortCode, boolean failed, String turnstileSiteKey) {
    boolean hasTurnstile = turnstileSiteKey != null && !turnstileSiteKey.isBlank();
    String script =
        hasTurnstile
            ? "<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\" async defer></script>"
            : "";
    String widget =
        hasTurnstile
            ? "<div class=\"cf\"><div class=\"cf-turnstile\" data-sitekey=\""
                + escape(turnstileSiteKey)
                + "\" data-theme=\"auto\"></div></div>"
            : "";
    String error = failed ? "<p class=\"err\">비밀번호가 올바르지 않아요.</p>" : "";
    String inner =
        "<h1>비밀번호가 필요한 링크</h1>"
            + "<p>이 링크를 만든 사람이 설정한 비밀번호를 입력하세요.</p>"
            + "<form method=\"post\" action=\"/"
            + shortCode
            + "\"><label for=\"pw\">비밀번호</label>"
            + "<input id=\"pw\" type=\"password\" name=\"password\" autofocus required autocomplete=\"off\">"
            + widget
            + "<button type=\"submit\">열기</button>"
            + error
            + "</form>"
            + script;
    return page(shortCode + " · 비밀번호", inner);
  }

  private static String escape(String s) {
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
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
