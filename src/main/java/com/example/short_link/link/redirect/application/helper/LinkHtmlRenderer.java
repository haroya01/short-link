package com.example.short_link.link.redirect.application.helper;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Static-HTML interstitials rendered inline by the redirect / unlock flow — not-found, expired,
 * view-limit reached, blocked, password prompt, and the post-unlock kurl reveal. No external assets
 * (served from the redirect hot path), but responsive (desktop + mobile, 16px inputs = no iOS
 * zoom), dark-mode aware, and on-brand: a centered card with the kurl green mark and a gentle
 * entrance. The password prompt shakes on a wrong password and optionally renders a Cloudflare
 * Turnstile widget; a correct password animates a kurl mark, then forwards to the destination.
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
      border-radius:16px;padding:28px;box-shadow:0 1px 2px rgba(15,23,42,.04),0 10px 30px rgba(15,23,42,.05);\
      animation:rise .4s cubic-bezier(.16,1,.3,1)}
      @keyframes rise{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
      .mark{display:block;width:26px;height:auto;color:var(--brand);margin-bottom:18px}
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
      @keyframes shake{10%,90%{transform:translateX(-1px)}20%,80%{transform:translateX(2px)}\
      30%,50%,70%{transform:translateX(-6px)}40%,60%{transform:translateX(6px)}}
      .shake{animation:shake .5s cubic-bezier(.36,.07,.19,.97)}
      .shake input{border-color:var(--danger);box-shadow:0 0 0 3px rgba(220,38,38,.12)}
      .unlock{text-align:center}
      .unlock .mark{display:none}
      .bigmark{display:block;width:92px;height:auto;color:var(--brand);margin:2px auto 18px;\
      clip-path:inset(0 100% 0 0);animation:wipe .7s cubic-bezier(.16,1,.3,1) .1s forwards}
      @keyframes wipe{to{clip-path:inset(0 0 0 0)}}
      .bar{width:130px;height:4px;border-radius:2px;background:var(--border);overflow:hidden;margin:16px auto 0}
      .bar span{display:block;height:100%;background:var(--brand);transform-origin:left;\
      transform:scaleX(0);animation:fill 1.25s linear .3s forwards}
      @keyframes fill{to{transform:scaleX(1)}}
      .pow{font-size:12px;color:var(--muted);margin-top:14px}.pow b{color:var(--brand);font-weight:600}
      """;

  /** Canonical kurl mark — same geometry as the web Logo (components/common/logo.tsx) and iOS. */
  private static String markSvg(String cls) {
    return "<svg class=\""
        + cls
        + "\" viewBox=\"0 0 28 18\" fill=\"currentColor\" aria-hidden=\"true\">"
        + "<rect x=\"6\" y=\"1\" width=\"20\" height=\"3.4\" rx=\"1.7\"/>"
        + "<rect x=\"0\" y=\"7.3\" width=\"28\" height=\"3.4\" rx=\"1.7\"/>"
        + "<rect x=\"9\" y=\"13.6\" width=\"17\" height=\"3.4\" rx=\"1.7\"/></svg>";
  }

  private static String page(String title, String inner) {
    return page(title, inner, "", "");
  }

  private static String page(String title, String inner, String cardClass, String headExtra) {
    return "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + headExtra
        + "<title>"
        + title
        + "</title><style>"
        + STYLE
        + "</style></head><body><main class=\"card"
        + cardClass
        + "\">"
        + markSvg("mark")
        + inner
        + "</main></body></html>";
  }

  public static ResponseEntity<byte[]> expiredPageResponse(String message) {
    return htmlResponse(HttpStatus.GONE, expiredPage(message));
  }

  public static ResponseEntity<byte[]> blockedPageResponse() {
    return htmlResponse(HttpStatus.FORBIDDEN, blockedPage());
  }

  public static ResponseEntity<byte[]> notFoundPageResponse() {
    return htmlResponse(HttpStatus.NOT_FOUND, notFoundPage());
  }

  public static ResponseEntity<byte[]> viewLimitPageResponse() {
    return htmlResponse(HttpStatus.GONE, viewLimitPage());
  }

  public static ResponseEntity<byte[]> passwordPromptResponse(
      HttpStatus status, ShortCode shortCode, boolean failed, String turnstileSiteKey) {
    return htmlResponse(status, passwordPrompt(shortCode, failed, turnstileSiteKey));
  }

  /** Post-unlock kurl reveal — animates, then forwards to {@code destinationUrl}. */
  public static ResponseEntity<byte[]> unlockedPageResponse(String destinationUrl) {
    return htmlResponse(HttpStatus.OK, unlockedPage(destinationUrl));
  }

  /**
   * Maps a visitor-facing terminal error to its branded HTML page so the bare {@code /{code}} link
   * never shows raw JSON. Returns {@code null} for codes that aren't visitor-facing, so the caller
   * propagates them to the API error handler.
   */
  public static ResponseEntity<byte[]> visitorErrorPage(LinkErrorCode code) {
    return switch (code) {
      case LINK_NOT_FOUND -> notFoundPageResponse();
      case LINK_EXPIRED -> expiredPageResponse(null);
      case LINK_VIEW_LIMIT_EXCEEDED -> viewLimitPageResponse();
      default -> null;
    };
  }

  static String expiredPage(String message) {
    String body =
        (message == null || message.isBlank())
            ? "<p>설정된 기간이 지났거나 만든 사람이 만료시킨 링크예요.</p>"
            : "<p>" + escape(message) + "</p>";
    return page("Link no longer available", "<h1>이 링크는 더 이상 열 수 없어요</h1>" + body);
  }

  static String blockedPage() {
    return page(
        "Not available", "<h1>이 지역에서는 열 수 없는 링크예요</h1>" + "<p>잘못된 것 같다면 링크를 만든 사람에게 문의하세요.</p>");
  }

  static String notFoundPage() {
    return page(
        "Link not found",
        "<h1>찾을 수 없는 링크예요</h1>" + "<p>주소가 틀렸거나, 삭제됐거나, 아직 만들어지지 않은 링크일 수 있어요.</p>");
  }

  static String viewLimitPage() {
    return page(
        "Link no longer available",
        "<h1>조회 한도에 도달한 링크예요</h1>"
            + "<p>이 링크는 만든 사람이 정한 최대 조회수를 모두 채워 더 이상 열 수 없어요.\n새 링크가 필요하면 만든 사람에게 문의하세요.</p>");
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
    // 비밀번호 오류 시 카드를 흔들어(shake) 즉각적인 피드백을 준다.
    return page(shortCode + " · 비밀번호", inner, failed ? " shake" : "", "");
  }

  static String unlockedPage(String destinationUrl) {
    String safe = escape(destinationUrl);
    // no-JS 폴백(3초) + JS 즉시(1.3초). URL 은 HTML 이스케이프해 data 속성에 싣고 JS 가 DOM 에서 읽어
    // 문자열 인젝션 없이 location.replace 한다.
    String head = "<meta http-equiv=\"refresh\" content=\"3; url=" + safe + "\">";
    String inner =
        "<div class=\"unlock\">"
            + markSvg("bigmark")
            + "<h1>열렸어요</h1>"
            + "<p>kurl 로 안전하게 잠금을 풀었어요.\n곧 이동합니다.</p>"
            + "<div class=\"bar\"><span></span></div>"
            + "<p class=\"pow\"><b>kurl</b> 로 단축한 링크</p>"
            + "<span id=\"d\" data-u=\""
            + safe
            + "\" hidden></span>"
            + "</div>"
            + "<script>setTimeout(function(){var u=document.getElementById('d').dataset.u;"
            + "if(u){location.replace(u)}},1300)</script>";
    return page("여는 중…", inner, " unlock", head);
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
