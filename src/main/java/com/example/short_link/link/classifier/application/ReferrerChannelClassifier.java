package com.example.short_link.link.classifier.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReferrerChannelClassifier {

  private static final Map<String, String> DOMAIN_TO_CHANNEL =
      Map.ofEntries(
          Map.entry("instagram.com", "social"),
          Map.entry("facebook.com", "social"),
          Map.entry("twitter.com", "social"),
          Map.entry("x.com", "social"),
          Map.entry("youtube.com", "social"),
          Map.entry("tiktok.com", "social"),
          Map.entry("linkedin.com", "social"),
          Map.entry("reddit.com", "social"),
          Map.entry("threads.net", "social"),
          Map.entry("pinterest.com", "social"),
          Map.entry("bsky.app", "social"),
          Map.entry("mastodon.social", "social"),
          Map.entry("medium.com", "social"),
          Map.entry("tumblr.com", "social"),
          Map.entry("twitch.tv", "social"),
          Map.entry("news.ycombinator.com", "social"),
          Map.entry("story.kakao.com", "social"),
          // 메신저도 referrer를 보내는 웹 클라이언트만 식별할 수 있다.
          Map.entry("discord.com", "messaging"),
          Map.entry("discordapp.com", "messaging"),
          Map.entry("t.me", "messaging"),
          Map.entry("telegram.org", "messaging"),
          Map.entry("web.telegram.org", "messaging"),
          Map.entry("slack.com", "messaging"),
          Map.entry("web.whatsapp.com", "messaging"),
          Map.entry("line.me", "messaging"),
          Map.entry("open.kakao.com", "messaging"),
          Map.entry("substack.com", "newsletter"),
          Map.entry("google.com", "search"),
          Map.entry("bing.com", "search"),
          Map.entry("naver.com", "search"),
          Map.entry("daum.net", "search"),
          Map.entry("duckduckgo.com", "search"),
          Map.entry("yahoo.com", "search"),
          Map.entry("baidu.com", "search"),
          Map.entry("mail.google.com", "email"),
          Map.entry("outlook.live.com", "email"),
          Map.entry("outlook.office.com", "email"),
          Map.entry("mail.naver.com", "email"),
          Map.entry("mail.daum.net", "email"));

  public String classify(String referrer) {
    if (referrer == null || referrer.isBlank()) {
      return "direct";
    }
    return classifyHost(extractHost(referrer));
  }

  public String classifyHost(String host) {
    if (host == null) {
      return "other";
    }
    String domain = host.toLowerCase(Locale.ROOT);
    while (true) {
      String channel = DOMAIN_TO_CHANNEL.get(domain);
      if (channel != null) {
        return channel;
      }
      int dot = domain.indexOf('.');
      if (dot < 0) {
        return "other";
      }
      domain = domain.substring(dot + 1);
    }
  }

  private static String extractHost(String url) {
    try {
      return new URI(url).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
