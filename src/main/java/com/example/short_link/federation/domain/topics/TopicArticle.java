package com.example.short_link.federation.domain.topics;

public record TopicArticle(String url, String title, String source, String image) {

  public static final TopicArticle NONE = new TopicArticle(null, null, null, null);
}
