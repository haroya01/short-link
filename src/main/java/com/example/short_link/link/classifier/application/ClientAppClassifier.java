package com.example.short_link.link.classifier.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Names the in-app browser a click was opened in, from the user-agent string. KakaoTalk, Instagram,
 * LINE and friends embed a WebView and stamp their own token onto the UA, so a link opened from a
 * chat room is distinguishable from the same link opened in Safari.
 *
 * <p>This is deliberately <em>not</em> a bot signal. Someone reading inside the KakaoTalk WebView
 * is a person; the in-app answer says <em>where</em> they were, never <em>whether</em> they were
 * real. {@code is_bot} is decided elsewhere and this classifier never touches it.
 *
 * <p>Returns {@code null} for an ordinary browser (and for a missing UA) — "not in an app" is the
 * common case and stays as a NULL column rather than a magic string, so aggregation can simply ask
 * for {@code client_app IS NOT NULL}.
 */
@Component
public class ClientAppClassifier {

  /** Longest value the {@code click_event.client_app} column accepts. */
  public static final int MAX_LENGTH = 32;

  /**
   * Ordered on purpose. Facebook's WebView often carries {@code Instagram} <em>and</em> {@code
   * FBAV} in the same UA (they share the browser stack), so Instagram is decided first and Facebook
   * only catches what is left. The same ordering is mirrored by the V117 backfill.
   */
  private static final List<Rule> RULES =
      List.of(
          new Rule("kakaotalk", "kakaotalk"),
          new Rule("instagram", "instagram"),
          // "Line/" with the slash — bare "line" appears inside unrelated tokens (e.g. "Headless",
          // "Streamline"), and LINE's WebView always writes the version as Line/x.y.z.
          new Rule("line/", "line"),
          new Rule("fbav", "facebook"),
          new Rule("fb_iab", "facebook"),
          new Rule("naver(inapp", "naver"),
          new Rule("daumapps", "daum"),
          // TikTok ships both the legacy musical_ly token and the newer TikTok one.
          new Rule("musical_ly", "tiktok"),
          new Rule("tiktok", "tiktok"),
          new Rule("twitter", "twitter"));

  /** In-app browser name, or {@code null} when this looks like an ordinary browser. */
  public String classify(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) return null;
    String lower = userAgent.toLowerCase(Locale.ROOT);
    for (Rule rule : RULES) {
      if (lower.contains(rule.token())) return rule.app();
    }
    return null;
  }

  private record Rule(String token, String app) {}
}
