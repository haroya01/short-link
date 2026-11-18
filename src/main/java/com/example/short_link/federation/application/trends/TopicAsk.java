package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;

public record TopicAsk(String tag, String url) {

  public static TopicAsk of(String tag, String url) {
    String name = bare(tag);
    String address = bare(url);
    if (name != null && name.startsWith("#")) {
      name = bare(name.substring(1));
    }
    if ((name == null) == (address == null)) {
      throw new FederationException(FederationErrorCode.TOPIC_ASK_INVALID);
    }
    return new TopicAsk(name, address);
  }

  private static String bare(String asked) {
    return asked == null || asked.isBlank() ? null : asked.strip();
  }

  String id() {
    return tag != null ? Topic.tagId(tag) : Topic.linkId(url);
  }

  NoteTopicReader.Carrying carrying() {
    return new NoteTopicReader.Carrying(tag, url);
  }
}
