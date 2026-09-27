package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Interstitials are self-contained on the redirect path. Keep inputs at 16px to avoid iOS focus
 * zoom.
 */
@Component
@RequiredArgsConstructor
public class LinkHtmlRenderer {

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
      .stay{display:inline-block;margin-top:18px;font-size:14px;font-weight:600;color:var(--brand);\
      text-decoration:none}
      .splash .msg{font-size:16px;color:var(--ink);word-break:break-word}
      .splash .btn{display:block;margin-top:18px;padding:13px;border-radius:10px;background:var(--brand);\
      color:#fff;text-align:center;font-size:15px;font-weight:600;text-decoration:none}
      .splash .go{display:inline-block;margin-top:14px;font-size:14px;font-weight:600;color:var(--brand);\
      text-decoration:none}
      .splash .bar{width:100%;margin:18px 0 0}
      .splash .bar span{animation-delay:0s}
      .splash .count{font-size:12px;margin-top:8px}
      .splash .stay{margin-top:10px;font-size:13px;font-weight:500;color:var(--muted)}
      """;

  private final MessageSource messages;

  /** Canonical kurl mark — same geometry as the web Logo (components/common/logo.tsx) and iOS. */
  private static String markSvg(String cls) {
    return "<svg class=\""
        + cls
        + "\" viewBox=\"0 0 28 18\" fill=\"currentColor\" aria-hidden=\"true\">"
        + "<rect x=\"6\" y=\"1\" width=\"20\" height=\"3.4\" rx=\"1.0\"/>"
        + "<rect x=\"0\" y=\"7.3\" width=\"28\" height=\"3.4\" rx=\"1.0\"/>"
        + "<rect x=\"9\" y=\"13.6\" width=\"17\" height=\"3.4\" rx=\"1.0\"/></svg>";
  }

  private String text(Locale locale, String key) {
    return messages.getMessage(key, null, key, locale);
  }

  private String page(Locale locale, String title, String inner) {
    return page(locale, title, inner, "", "");
  }

  private String page(
      Locale locale, String title, String inner, String cardClass, String headExtra) {
    return "<!doctype html><html lang=\""
        + locale.getLanguage()
        + "\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + headExtra
        + "<title>"
        + escape(title)
        + "</title><style>"
        + STYLE
        + "</style></head><body><main class=\"card"
        + cardClass
        + "\">"
        + markSvg("mark")
        + inner
        + "</main></body></html>";
  }

  private String notice(Locale locale, String key) {
    String title = text(locale, key + ".title");
    return page(
        locale,
        title,
        "<h1>" + escape(title) + "</h1><p>" + escape(text(locale, key + ".body")) + "</p>");
  }

  public ResponseEntity<byte[]> expiredPageResponse(Locale locale, String message) {
    return htmlResponse(HttpStatus.GONE, expiredPage(locale, message));
  }

  public ResponseEntity<byte[]> blockedPageResponse(Locale locale) {
    return htmlResponse(HttpStatus.FORBIDDEN, notice(locale, "visitor.blocked"));
  }

  public ResponseEntity<byte[]> domainBlockedPageResponse(Locale locale) {
    return htmlResponse(HttpStatus.FORBIDDEN, notice(locale, "visitor.domainBlocked"));
  }

  public ResponseEntity<byte[]> notFoundPageResponse(Locale locale) {
    return htmlResponse(HttpStatus.NOT_FOUND, notice(locale, "visitor.notFound"));
  }

  public ResponseEntity<byte[]> viewLimitPageResponse(Locale locale) {
    return htmlResponse(HttpStatus.GONE, notice(locale, "visitor.viewLimit"));
  }

  public ResponseEntity<byte[]> passwordPromptResponse(
      Locale locale,
      HttpStatus status,
      ShortCode shortCode,
      boolean failed,
      String turnstileSiteKey) {
    return htmlResponse(status, passwordPrompt(locale, shortCode, failed, turnstileSiteKey));
  }

  public ResponseEntity<byte[]> unlockedPageResponse(Locale locale, String destinationUrl) {
    return htmlResponse(HttpStatus.OK, unlockedPage(locale, destinationUrl));
  }

  public VisitPage splashPageResponse(
      Locale locale, CachedLink.Splash splash, String nextUrl, String stayUrl) {
    return visitPage(splashPage(locale, splash, nextUrl, stayUrl));
  }

  public VisitPage inAppHandoffPageResponse(
      Locale locale, String handoffUrl, String destinationUrl) {
    return visitPage(inAppHandoffPage(locale, handoffUrl, destinationUrl));
  }

  /**
   * Returns null for errors that should propagate to the API error handler; visitor errors render
   * HTML.
   */
  public ResponseEntity<byte[]> visitorErrorPage(Locale locale, LinkErrorCode code) {
    return switch (code) {
      case LINK_NOT_FOUND -> notFoundPageResponse(locale);
      case LINK_EXPIRED -> expiredPageResponse(locale, null);
      case LINK_VIEW_LIMIT_EXCEEDED -> viewLimitPageResponse(locale);
      default -> null;
    };
  }

  String expiredPage(Locale locale, String message) {
    String title = text(locale, "visitor.expired.title");
    String body =
        (message == null || message.isBlank()) ? text(locale, "visitor.expired.body") : message;
    return page(locale, title, "<h1>" + escape(title) + "</h1><p>" + escape(body) + "</p>");
  }

  String passwordPrompt(
      Locale locale, ShortCode shortCode, boolean failed, String turnstileSiteKey) {
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
    String error =
        failed ? "<p class=\"err\">" + escape(text(locale, "visitor.password.wrong")) + "</p>" : "";
    String inner =
        "<h1>"
            + escape(text(locale, "visitor.password.title"))
            + "</h1>"
            + "<p>"
            + escape(text(locale, "visitor.password.body"))
            + "</p>"
            + "<form method=\"post\" action=\"/"
            + shortCode
            + "\"><label for=\"pw\">"
            + escape(text(locale, "visitor.password.label"))
            + "</label>"
            + "<input id=\"pw\" type=\"password\" name=\"password\" autofocus required autocomplete=\"off\">"
            + widget
            + "<button type=\"submit\">"
            + escape(text(locale, "visitor.password.submit"))
            + "</button>"
            + error
            + "</form>"
            + script;
    return page(
        locale,
        shortCode + " · " + text(locale, "visitor.password.label"),
        inner,
        failed ? " shake" : "",
        "");
  }

  String unlockedPage(Locale locale, String destinationUrl) {
    String safe = escape(destinationUrl);
    // URL은 HTML data 속성에서 읽어 JS 문자열 삽입을 피한다. JS가 없으면 meta-refresh로 이동한다.
    String head = "<meta http-equiv=\"refresh\" content=\"3; url=" + safe + "\">";
    String inner =
        "<div class=\"unlock\">"
            + markSvg("bigmark")
            + "<h1>"
            + escape(text(locale, "visitor.unlocked.title"))
            + "</h1>"
            + "<p>"
            + escape(text(locale, "visitor.unlocked.body"))
            + "</p>"
            + "<div class=\"bar\"><span></span></div>"
            + "<p class=\"pow\">"
            + poweredBy(locale)
            + "</p>"
            + "<span id=\"d\" data-u=\""
            + safe
            + "\" hidden></span>"
            + "</div>"
            + "<script>setTimeout(function(){var u=document.getElementById('d').dataset.u;"
            + "if(u){location.replace(u)}},1300)</script>";
    return page(locale, text(locale, "visitor.unlocked.pageTitle"), inner, " unlock", head);
  }

  String splashPage(Locale locale, CachedLink.Splash splash, String nextUrl, String stayUrl) {
    int seconds = Math.max(1, splash.seconds());
    String cta =
        splash.ctaLabel() == null || splash.ctaUrl() == null
            ? ""
            : "<a class=\"btn\" href=\""
                + escape(splash.ctaUrl())
                + "\">"
                + escape(splash.ctaLabel())
                + "</a>";
    String stay =
        stayUrl == null
            ? ""
            : "<a class=\"stay\" href=\""
                + escape(stayUrl)
                + "\">"
                + escape(text(locale, "visitor.handoff.stay"))
                + "</a>";
    String inner =
        "<p class=\"msg\">"
            + escape(splash.message())
            + "</p>"
            + cta
            + "<a class=\"go\" href=\""
            + escape(nextUrl)
            + "\">"
            + escape(text(locale, "visitor.splash.continue"))
            + " →</a>"
            + "<div class=\"bar\"><span style=\"animation-duration:"
            + seconds
            + "s\"></span></div>"
            + "<p class=\"count\" id=\"c\">"
            + withMarker(
                locale, "visitor.splash.countdown", "<span id=\"n\">" + seconds + "</span>")
            + "</p>"
            + stay
            + "<p class=\"pow\">"
            + poweredBy(locale)
            + "</p>"
            + "<span id=\"d\" data-u=\""
            + escape(nextUrl)
            + "\" data-s=\""
            + seconds
            + "\" data-p=\""
            + escape(text(locale, "visitor.splash.paused"))
            + "\" hidden></span>"
            + "<script>(function(){var d=document.getElementById('d'),s=+d.dataset.s,"
            + "n=document.getElementById('n'),c=document.getElementById('c'),"
            + "b=document.querySelector('.bar span');"
            + "var t=setInterval(function(){s--;if(s<=0){clearInterval(t);location.replace(d.dataset.u);return}"
            + "n.textContent=s},1000);"
            + "function stop(){clearInterval(t);c.textContent=d.dataset.p;if(b){b.style.animationPlayState='paused'}}"
            + "document.addEventListener('pointerdown',stop,{once:true});"
            + "document.addEventListener('keydown',stop,{once:true})})()</script>";
    return page(locale, text(locale, "visitor.splash.pageTitle"), inner, " splash", "");
  }

  String inAppHandoffPage(Locale locale, String handoffUrl, String destinationUrl) {
    String title = text(locale, "visitor.handoff.title");
    String inner =
        "<h1>"
            + escape(title)
            + "</h1><p>"
            + escape(text(locale, "visitor.handoff.body"))
            + "</p><a class=\"stay\" href=\""
            + escape(destinationUrl)
            + "\">"
            + escape(text(locale, "visitor.handoff.stay"))
            + "</a><span id=\"d\" data-u=\""
            + escape(handoffUrl)
            + "\" hidden></span>"
            + "<script>var u=document.getElementById('d').dataset.u;if(u){location.replace(u)}</script>";
    return page(locale, title, inner);
  }

  private String poweredBy(Locale locale) {
    return withMarker(locale, "visitor.poweredBy", "<b>kurl</b>");
  }

  private String withMarker(Locale locale, String key, String html) {
    String marker = "\u0000";
    String line = messages.getMessage(key, new Object[] {marker}, locale);
    return escape(line).replace(marker, html);
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
    return ResponseEntity.status(status).headers(htmlHeaders(bytes.length)).body(bytes);
  }

  private static VisitPage visitPage(String html) {
    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
    return new VisitPage(bytes, htmlHeaders(bytes.length));
  }

  private static HttpHeaders htmlHeaders(int length) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/html; charset=utf-8"));
    headers.setContentLength(length);
    headers.set("X-Robots-Tag", "noindex, nofollow");
    headers.set(HttpHeaders.VARY, "Accept-Language");
    return headers;
  }
}
