package com.example.short_link.federation.domain.topics;

public record TopicSighting(
    String host, int place, String tag, TopicArticle article, String language, int people) {

  public static TopicSighting tag(String host, int place, String name, int people) {
    return new TopicSighting(
        host, place, name, TopicArticle.NONE, TopicLanguage.ofScript(name), people);
  }

  public static TopicSighting link(
      String host, int place, TopicArticle article, String language, int people) {
    return new TopicSighting(host, place, null, article, language, people);
  }

  String key() {
    return tag != null ? Topic.tagId(tag) : Topic.linkId(article.url());
  }
}
