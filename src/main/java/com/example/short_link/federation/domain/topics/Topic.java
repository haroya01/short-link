package com.example.short_link.federation.domain.topics;

import java.util.List;
import java.util.Locale;

public record Topic(
    String tag,
    TopicArticle article,
    String language,
    int people,
    int place,
    List<String> servers,
    List<TopicArticle> related) {

  public static String tagId(String name) {
    return "tag:" + name.toLowerCase(Locale.ROOT);
  }

  public static String linkId(String url) {
    return "link:" + url;
  }

  public String id() {
    return isTag() ? tagId(tag) : linkId(article.url());
  }

  public boolean isTag() {
    return tag != null;
  }

  public Topic in(String known) {
    return new Topic(tag, article, known, people, place, servers, related);
  }

  public Topic on(List<String> left) {
    return new Topic(tag, article, language, people, place, left, related);
  }

  Topic with(List<TopicArticle> folded, int largest) {
    return new Topic(tag, article, language, largest, place, servers, folded);
  }
}
