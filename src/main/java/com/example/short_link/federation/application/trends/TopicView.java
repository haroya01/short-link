package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicArticle;
import com.example.short_link.federation.domain.topics.TopicRanking;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import java.util.Set;

@JsonPropertyOrder({
  "id",
  "kind",
  "tag",
  "article",
  "language",
  "own",
  "people",
  "servers",
  "related"
})
public record TopicView(
    String tag,
    @JsonUnwrapped TopicArticle article,
    String language,
    boolean own,
    int people,
    List<String> servers,
    List<TopicArticle> related) {

  static TopicView of(Topic topic, Set<String> ownLanguages) {
    return new TopicView(
        topic.tag(),
        topic.article(),
        topic.language(),
        TopicRanking.own(topic, ownLanguages),
        topic.people(),
        topic.servers(),
        topic.related());
  }

  @JsonProperty
  public String id() {
    return tag != null ? Topic.tagId(tag) : Topic.linkId(article.url());
  }

  @JsonProperty
  public String kind() {
    return tag != null ? "tag" : "link";
  }
}
