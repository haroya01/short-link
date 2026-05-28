package com.example.short_link.link.redirect.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Decides whether a request should get OG-tagged HTML instead of a 302. Intentionally separate from
 * {@link UserAgentClassifier}, which classifies for analytics — a given crawler is usually both,
 * but the two lists are not identical.
 */
@Component
public class LinkPreviewCrawlerDetector {

  private static final List<String> CRAWLER_TOKENS =
      List.of(
          "kakaotalk-scrap",
          "slackbot-linkexpanding",
          "slackbot",
          "twitterbot",
          "facebookexternalhit",
          "facebot",
          "linkedinbot",
          "discordbot",
          "whatsapp",
          "telegrambot",
          "skypeuripreview",
          "embedly",
          "pinterest",
          "redditbot",
          "applebot",
          "vkshare",
          "xing-contenttabreceiver",
          "iframely",
          "nuzzel",
          "googleplus",
          "googlebot");

  /**
   * Returns the matched crawler token (lowercase) so callers can attach it as a bot name when
   * persisting the preview hit — yauaa would otherwise drop it as "unknown" since most messenger
   * crawlers aren't in its analyzer data.
   */
  public String crawlerName(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) return null;
    String lower = userAgent.toLowerCase(Locale.ROOT);
    for (String token : CRAWLER_TOKENS) {
      if (lower.contains(token)) return token;
    }
    return null;
  }
}
