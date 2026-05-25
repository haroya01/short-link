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
