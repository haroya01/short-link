package com.example.short_link.link.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Identifies link-preview crawlers (KakaoTalk, Slack, Twitter, Facebook, LinkedIn, Discord,
 * WhatsApp, Telegram, etc.) by User-Agent substring. These bots want OG metadata, not the actual
 * destination — so the redirect handler should respond with an OG-tagged HTML page instead of a
 * 302.
 *
 * <p>This is intentionally separate from {@link UserAgentClassifier}: that one classifies for
 * analytics ("is this a bot click"), while this one decides response shape. A given crawler is
 * usually both, but the lists are not identical.
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

  public boolean isCrawler(String userAgent) {
    return crawlerName(userAgent) != null;
  }

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
