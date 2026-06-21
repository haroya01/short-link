package com.example.short_link.link.classifier.application;

import java.net.URI;
import java.net.URISyntaxException;
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
          // dark-social / 게시·커뮤니티 추가 — 이전엔 전부 'other' 로 뭉개지던 진짜 채널들.
          Map.entry("bsky.app", "social"),
          Map.entry("mastodon.social", "social"),
          Map.entry("medium.com", "social"),
          Map.entry("tumblr.com", "social"),
          Map.entry("twitch.tv", "social"),
          Map.entry("news.ycombinator.com", "social"),
          Map.entry("story.kakao.com", "social"),
          // 메신저(다크 소셜) — referrer 를 보내는 웹 클라이언트는 잡힌다(DM/그룹 공유).
          Map.entry("discord.com", "messaging"),
          Map.entry("discordapp.com", "messaging"),
          Map.entry("t.me", "messaging"),
          Map.entry("telegram.org", "messaging"),
          Map.entry("web.telegram.org", "messaging"),
          Map.entry("slack.com", "messaging"),
          Map.entry("web.whatsapp.com", "messaging"),
          Map.entry("line.me", "messaging"),
          Map.entry("open.kakao.com", "messaging"),
          // 뉴스레터·구독 출판
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
    String host = extractHost(referrer);
    if (host == null) {
      return "other";
    }
    String lower = host.toLowerCase();
    String exact = DOMAIN_TO_CHANNEL.get(lower);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, String> entry : DOMAIN_TO_CHANNEL.entrySet()) {
      if (lower.endsWith("." + entry.getKey())) {
        return entry.getValue();
      }
    }
    return "other";
  }

  private static String extractHost(String url) {
    try {
      return new URI(url).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
