package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicArticle;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class TopicFixtures {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
  static final TrendingStatusReader READER =
      new TrendingStatusReader(new RemoteNoteParser(URLS), JSON);

  private TopicFixtures() {}

  static String history(int... accountsNewestFirst) {
    StringBuilder days = new StringBuilder("[");
    for (int day = 0; day < accountsNewestFirst.length; day++) {
      days.append(day == 0 ? "" : ",")
          .append("{\"day\":\"")
          .append(1_790_000_000 - day * 86_400)
          .append("\",\"accounts\":\"")
          .append(accountsNewestFirst[day])
          .append("\",\"uses\":\"")
          .append(accountsNewestFirst[day] * 2)
          .append("\"}");
    }
    return days.append("]").toString();
  }

  static String tagRow(String name, int... accountsNewestFirst) {
    return "{\"name\":%s,\"url\":\"https://a.example/tags/x\",\"history\":%s}"
        .formatted(JSON.writeValueAsString(name), history(accountsNewestFirst));
  }

  static String linkRow(String url, String title, String language, int... accountsNewestFirst) {
    return """
        {"url":%s,"title":%s,"description":"","type":"link","provider_name":"Example Daily",
         "image":"https://img.example/a.png","language":%s,"history":%s}"""
        .formatted(
            JSON.writeValueAsString(url),
            JSON.writeValueAsString(title),
            JSON.writeValueAsString(language),
            history(accountsNewestFirst));
  }

  static String rows(String... rows) {
    return "[" + String.join(",", rows) + "]";
  }

  static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(200, body.getBytes(StandardCharsets.UTF_8));
  }

  static URI tagsOf(String host) {
    return URI.create("https://" + host + "/api/v1/trends/tags?limit=20");
  }

  static URI linksOf(String host) {
    return URI.create("https://" + host + "/api/v1/trends/links?limit=20");
  }

  static Topic tag(String name, String... servers) {
    return new Topic(name, TopicArticle.NONE, null, 5, 0, List.of(servers), List.of());
  }

  static Topic link(String url, String... servers) {
    return new Topic(
        null,
        new TopicArticle(url, "A headline", "Example Daily", null),
        "en",
        5,
        0,
        List.of(servers),
        List.of());
  }

  static String uri(String host, String user, int id) {
    return "https://" + host + "/users/" + user + "/statuses/" + id;
  }

  static ObjectNode status(String uri, Instant createdAt, String language) {
    String user = uri.substring(uri.indexOf("/users/") + 7, uri.indexOf("/statuses/"));
    ObjectNode status = TrendingStatusReaderTest.status(uri, user);
    status.put("created_at", createdAt.toString());
    if (language == null) {
      status.putNull("language");
    } else {
      status.put("language", language);
    }
    return status;
  }

  static TrendingStatusReader.Read read(String uri, Instant createdAt) {
    String host = URI.create(uri).getHost();
    return READER.read(status(uri, createdAt, "en"), host).orElseThrow();
  }

  static TrendingStatusReader.Read read(String uri) {
    return read(uri, Instant.parse("2026-11-18T08:00:00Z"));
  }
}
