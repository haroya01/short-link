package com.example.short_link.link.classifier.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * In-app browser identity is independent of bot classification. Ordinary browsers and missing UAs
 * return null, allowing aggregates to exclude them with {@code client_app IS NOT NULL}.
 */
@Component
public class ClientAppClassifier {

  /** Longest value the {@code click_event.client_app} column accepts. */
  public static final int MAX_LENGTH = 32;

  /**
   * Instagram must precede Facebook because a UA can contain both tokens. Keep this ordering
   * aligned with the V117 backfill.
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
