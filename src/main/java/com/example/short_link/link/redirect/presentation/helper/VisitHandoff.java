package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitHandoff {

  private static final String LINE_EXTERNAL_PARAM = "openExternalBrowser=1";
  private static final String KAKAOTALK_EXTERNAL = "kakaotalk://web/openExternal?url=";

  private final ClientAppClassifier clientApps;
  private final LinkHtmlRenderer html;

  public ResponseEntity<?> redirect(
      RedirectOutcome.Redirect redirect, String userAgent, Locale locale) {
    String destination = redirect.picked().url();
    String app = inAppBrowser(redirect, userAgent);
    if ("kakaotalk".equals(app)) {
      return html.inAppHandoffPageResponse(locale, kakaoTalkExternal(destination), destination);
    }
    String location =
        "line".equals(app) ? withParam(destination, LINE_EXTERNAL_PARAM) : destination;
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(location))
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=90")
        .header("X-Robots-Tag", "noindex, nofollow")
        .build();
  }

  public ResponseEntity<byte[]> unlocked(
      RedirectOutcome.Redirect redirect, String userAgent, Locale locale) {
    String destination = redirect.picked().url();
    String app = inAppBrowser(redirect, userAgent);
    String next =
        switch (app == null ? "" : app) {
          case "kakaotalk" -> kakaoTalkExternal(destination);
          case "line" -> withParam(destination, LINE_EXTERNAL_PARAM);
          default -> destination;
        };
    return html.unlockedPageResponse(locale, next);
  }

  private String inAppBrowser(RedirectOutcome.Redirect redirect, String userAgent) {
    return redirect.visitOptions().openInBrowser() ? clientApps.classify(userAgent) : null;
  }

  private static String kakaoTalkExternal(String destination) {
    return KAKAOTALK_EXTERNAL + URLEncoder.encode(destination, StandardCharsets.UTF_8);
  }

  static String withParam(String url, String param) {
    int hash = url.indexOf('#');
    String base = hash < 0 ? url : url.substring(0, hash);
    String fragment = hash < 0 ? "" : url.substring(hash);
    String separator;
    if (!base.contains("?")) {
      separator = "?";
    } else if (base.endsWith("?") || base.endsWith("&")) {
      separator = "";
    } else {
      separator = "&";
    }
    return base + separator + param + fragment;
  }
}
