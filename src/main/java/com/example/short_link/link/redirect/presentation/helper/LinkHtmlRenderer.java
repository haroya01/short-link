package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.WebAddress;
import com.example.short_link.link.exception.LinkErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
      :root{color-scheme:light;--bg:#fff;--ink:rgb(19 26 22);--ink-soft:rgb(59 66 62);\
      --muted:rgb(109 117 112);--line:rgb(208 213 210);--green:#059669;--on-ink:#fff;\
      --ink-press:rgb(36 43 39);--danger:#dc2626;--ease:cubic-bezier(.16,1,.3,1)}
      @media(prefers-color-scheme:dark){:root{color-scheme:dark;--bg:rgb(15 20 18);--ink:rgb(230 234 231);\
      --ink-soft:rgb(194 201 197);--muted:rgb(154 163 158);--line:rgb(54 62 57);--green:#10b981;\
      --on-ink:rgb(15 20 18);--ink-press:rgb(216 221 218);--danger:#f87171}}
      body{margin:0;min-height:100vh;min-height:100svh;display:grid;place-items:center;\
      padding:40px 16px calc(40px + 10vh);background:var(--bg);color:var(--ink);\
      font-family:'Pretendard Variable',Pretendard,-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;\
      -webkit-font-smoothing:antialiased}
      main{width:100%;max-width:360px;animation:enter .46s var(--ease) both}
      main.still{animation:none}
      @keyframes enter{from{opacity:0;transform:translateY(4px)}}
      :lang(ko){word-break:keep-all}
      h1,p{overflow-wrap:break-word;line-break:strict;text-wrap:pretty}
      .mark{display:block;width:22px;height:auto;color:var(--green);margin-bottom:28px}
      h1{font-size:21px;font-weight:700;line-height:1.35;letter-spacing:-.02em;margin:0 0 8px}
      p{font-size:15px;line-height:1.6;color:var(--muted);margin:0;white-space:pre-wrap}
      .sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}
      .row{position:relative;display:flex;align-items:center;justify-content:space-between;gap:12px;\
      min-height:48px;margin-top:28px}
      .row::after{content:"";position:absolute;left:0;right:0;bottom:0;height:1px;background:var(--line);\
      transition:background-color .2s var(--ease)}
      .field:focus-within::after{background:var(--ink)}
      input{flex:1;min-width:0;height:48px;padding:0;border:0;border-radius:0;background:none;color:var(--ink);\
      font:inherit;font-size:16px;outline:none}
      input::placeholder{color:var(--muted);opacity:1}
      button,.go{display:inline-flex;align-items:center;gap:6px;min-height:44px;margin:0;padding:0 0 0 8px;\
      border:0;background:none;color:var(--ink);font:inherit;font-size:15px;font-weight:600;\
      text-decoration:none;white-space:nowrap;cursor:pointer;transition:color .2s var(--ease)}
      button:hover,.go:hover{color:var(--ink-soft)}
      button span,.go span{transition:transform .2s var(--ease)}
      button:hover span,.go:hover span{transform:translateX(2px)}
      a:focus-visible,button:focus-visible{outline:2px solid var(--ink);outline-offset:2px;border-radius:4px}
      .miss{animation:nudge .28s var(--ease)}
      .miss::after{animation:flash 1.2s var(--ease)}
      @keyframes nudge{30%{transform:translateX(-4px)}70%{transform:translateX(4px)}}
      @keyframes flash{0%,50%{background:var(--danger)}}
      .err{display:flex;align-items:center;gap:8px;margin-top:10px;font-size:13px;font-weight:500;color:var(--danger)}
      .err::before{content:"";flex:none;width:14px;height:3px;border-radius:2px;background:currentColor}
      .cf{margin-top:20px}
      .line{position:absolute;left:0;right:0;bottom:0;z-index:1;height:2px;border-radius:2px;background:var(--green);\
      transform-origin:left;animation:draw .64s var(--ease) .06s backwards}
      @keyframes draw{from{transform:scaleX(0)}}
      .count,.pow{font-size:13px;line-height:1.5;color:var(--muted)}
      .pow b{color:var(--ink);font-weight:600}
      p.count{margin-top:16px}
      .stay{display:inline-flex;align-items:center;min-height:44px;margin-top:16px;font-size:15px;font-weight:600;\
      color:var(--ink);text-decoration:underline;text-decoration-color:var(--line);text-underline-offset:3px;\
      transition:text-decoration-color .2s var(--ease)}
      .stay:hover{text-decoration-color:var(--ink)}
      .splash .msg{font-size:17px;font-weight:500;line-height:1.55;letter-spacing:-.01em;color:var(--ink)}
      .splash .btn{display:inline-flex;align-items:center;min-height:44px;margin-top:20px;padding:0 20px;\
      border-radius:8px;background:var(--ink);color:var(--on-ink);font-size:15px;font-weight:600;text-decoration:none;\
      transition:background-color .2s var(--ease)}
      .splash .btn:hover{background:var(--ink-press)}
      .splash .line{background:var(--muted);animation-timing-function:linear;animation-delay:0s}
      .splash .row .count{padding:12px 0}
      .splash .stay{display:flex;width:max-content;margin-top:8px;font-size:13px;font-weight:500;color:var(--muted);\
      text-decoration:underline;text-decoration-color:var(--line);text-underline-offset:3px}
      .splash .stay:hover{color:var(--ink)}
      .splash .pow{margin-top:8px}
      @media(prefers-reduced-motion:reduce){*,::before,::after{animation:none!important;transition:none!important}}
      """;

  private static final DateTimeFormatter UTC_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  private final MessageSource messages;

  /** Canonical kurl mark — same geometry as the web Logo (components/common/logo.tsx) and iOS. */
  private static String markSvg() {
    return "<svg class=\"mark\" viewBox=\"0 0 28 18\" fill=\"currentColor\" aria-hidden=\"true\">"
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
      Locale locale, String title, String inner, String mainClass, String headExtra) {
    return "<!doctype html><html lang=\""
        + locale.getLanguage()
        + "\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + headExtra
        + "<title>"
        + escape(title)
        + "</title><style>"
        + STYLE
        + "</style></head><body><main"
        + (mainClass.isEmpty() ? "" : " class=\"" + mainClass + "\"")
        + ">"
        + markSvg()
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

  public ResponseEntity<byte[]> disabledPageResponse(Locale locale) {
    return htmlResponse(HttpStatus.GONE, notice(locale, "visitor.disabled"));
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
    return visitPage(
        passwordPrompt(locale, shortCode, failed, turnstileSiteKey), status, "password_required");
  }

  public ResponseEntity<byte[]> unlockedPageResponse(Locale locale, String destinationUrl) {
    return htmlResponse(HttpStatus.OK, unlockedPage(locale, destinationUrl));
  }

  public VisitPage splashPageResponse(
      Locale locale, CachedLink.Splash splash, String nextUrl, String stayUrl) {
    return visitPage(splashPage(locale, splash, nextUrl, stayUrl));
  }

  public VisitPage notYetOpenPageResponse(Locale locale, Instant opensAt) {
    return visitPage(notYetOpenPage(locale, opensAt), HttpStatus.FORBIDDEN, "not_open");
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
      case LINK_DISABLED -> disabledPageResponse(locale);
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
        failed
            ? "<p class=\"err\" id=\"err\">"
                + escape(text(locale, "visitor.password.wrong"))
                + "</p>"
            : "";
    String label = escape(text(locale, "visitor.password.label"));
    String inner =
        "<h1>"
            + escape(text(locale, "visitor.password.title"))
            + "</h1>"
            + "<p>"
            + escape(text(locale, "visitor.password.body"))
            + "</p>"
            + "<form method=\"post\" action=\"/"
            + shortCode
            + "\"><label class=\"sr\" for=\"pw\">"
            + label
            + "</label>"
            + "<div class=\"row field"
            + (failed ? " miss" : "")
            + "\"><input id=\"pw\" type=\"password\" name=\"password\" placeholder=\""
            + label
            + "\" autofocus required autocomplete=\"off\""
            + (failed ? " aria-invalid=\"true\" aria-describedby=\"err\"" : "")
            + "><button type=\"submit\">"
            + escape(text(locale, "visitor.password.submit"))
            + "<span aria-hidden=\"true\">→</span></button></div>"
            + error
            + widget
            + "</form>"
            + script;
    return page(
        locale,
        shortCode + " · " + text(locale, "visitor.password.label"),
        inner,
        failed ? "still" : "",
        "");
  }

  String unlockedPage(Locale locale, String destinationUrl) {
    String safe = escape(navigable(destinationUrl));
    // URL은 HTML data 속성에서 읽어 JS 문자열 삽입을 피한다. JS가 없으면 meta-refresh로 이동한다.
    String head =
        safe.isEmpty() ? "" : "<meta http-equiv=\"refresh\" content=\"3; url=" + safe + "\">";
    String inner =
        "<h1>"
            + escape(text(locale, "visitor.unlocked.title"))
            + "</h1>"
            + "<p>"
            + escape(text(locale, "visitor.unlocked.body"))
            + "</p>"
            + "<div class=\"row\"><span class=\"pow\">"
            + poweredBy(locale)
            + "</span><span class=\"line\"></span></div>"
            + "<span id=\"d\" data-u=\""
            + safe
            + "\" hidden></span>"
            + "<script>setTimeout(function(){var u=document.getElementById('d').dataset.u;"
            + "if(u){location.replace(u)}},1300)</script>";
    return page(locale, text(locale, "visitor.unlocked.pageTitle"), inner, "still", head);
  }

  String splashPage(Locale locale, CachedLink.Splash splash, String nextUrl, String stayUrl) {
    int seconds = Math.max(1, splash.seconds());
    String next = escape(navigable(nextUrl));
    String cta =
        splash.ctaLabel() == null || navigable(splash.ctaUrl()) == null
            ? ""
            : "<a class=\"btn\" href=\""
                + escape(splash.ctaUrl())
                + "\">"
                + escape(splash.ctaLabel())
                + "</a>";
    String stay =
        navigable(stayUrl) == null
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
            + "<div class=\"row\"><span class=\"count\" id=\"c\">"
            + withMarker(
                locale, "visitor.splash.countdown", "<span id=\"n\">" + seconds + "</span>")
            + "</span>"
            + (next.isEmpty()
                ? ""
                : "<a class=\"go\" href=\""
                    + next
                    + "\">"
                    + escape(text(locale, "visitor.splash.continue"))
                    + "<span aria-hidden=\"true\">→</span></a>")
            + "<span class=\"line\" style=\"animation-duration:"
            + seconds
            + "s\"></span></div>"
            + stay
            + "<p class=\"pow\">"
            + poweredBy(locale)
            + "</p>"
            + "<span id=\"d\" data-u=\""
            + next
            + "\" data-s=\""
            + seconds
            + "\" data-p=\""
            + escape(text(locale, "visitor.splash.paused"))
            + "\" hidden></span>"
            + "<script>(function(){var d=document.getElementById('d'),s=+d.dataset.s,"
            + "n=document.getElementById('n'),c=document.getElementById('c'),"
            + "b=document.querySelector('.line');"
            + "var t=setInterval(function(){s--;if(s<=0){clearInterval(t);if(d.dataset.u){location.replace(d.dataset.u)}return}"
            + "n.textContent=s},1000);"
            + "function stop(){clearInterval(t);c.textContent=d.dataset.p;if(b){b.style.animationPlayState='paused'}}"
            + "document.addEventListener('pointerdown',stop,{once:true});"
            + "document.addEventListener('keydown',stop,{once:true})})()</script>";
    return page(locale, text(locale, "visitor.splash.pageTitle"), inner, "splash", "");
  }

  String notYetOpenPage(Locale locale, Instant opensAt) {
    String title = text(locale, "visitor.notOpen.title");
    String when =
        "<time id=\"t\" datetime=\""
            + opensAt
            + "\">"
            + escape(UTC_TIME.format(opensAt))
            + "</time>";
    String inner =
        "<h1>"
            + escape(title)
            + "</h1><p>"
            + withMarker(locale, "visitor.notOpen.body", when)
            + "</p><p class=\"count\">"
            + escape(text(locale, "visitor.notOpen.wait"))
            + "</p>"
            + "<script>(function(){var t=document.getElementById('t'),at=Date.parse(t.dateTime);"
            + "try{t.textContent=new Date(at).toLocaleString(document.documentElement.lang,"
            + "{dateStyle:'long',timeStyle:'short'})}catch(e){}"
            + "var ms=at-Date.now();if(ms>0&&ms<2147483647){setTimeout(function(){location.reload()},ms+1000)}"
            + "})()</script>";
    return page(locale, title, inner);
  }

  String inAppHandoffPage(Locale locale, String handoffUrl, String destinationUrl) {
    String title = text(locale, "visitor.handoff.title");
    String inner =
        "<h1>"
            + escape(title)
            + "</h1><p>"
            + escape(text(locale, "visitor.handoff.body"))
            + "</p>"
            + (navigable(destinationUrl) == null
                ? ""
                : "<a class=\"stay\" href=\""
                    + escape(destinationUrl)
                    + "\">"
                    + escape(text(locale, "visitor.handoff.stay"))
                    + "</a>")
            + "<span id=\"d\" data-u=\""
            + escape(navigable(handoffUrl))
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

  private static String navigable(String url) {
    return WebAddress.is(url) || (url != null && url.startsWith(VisitHandoff.KAKAOTALK_EXTERNAL))
        ? url
        : null;
  }

  private static String escape(String s) {
    if (s == null) return "";
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
    return visitPage(html, HttpStatus.OK, "redirect");
  }

  private static VisitPage visitPage(String html, HttpStatus status, String outcome) {
    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
    return new VisitPage(bytes, htmlHeaders(bytes.length), status, outcome);
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
