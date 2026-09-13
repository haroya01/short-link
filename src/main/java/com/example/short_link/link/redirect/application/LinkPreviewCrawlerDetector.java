package com.example.short_link.link.redirect.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Preview rendering and analytics bot classification use different crawler lists; keep this
 * separate from {@link UserAgentClassifier}.
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
   * Returns the lowercase token so preview hits retain a bot name even when yauaa does not
   * recognize the crawler.
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
