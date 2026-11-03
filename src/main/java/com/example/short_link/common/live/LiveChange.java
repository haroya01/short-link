package com.example.short_link.common.live;

import java.util.Locale;

public record LiveChange(LiveTopic topic, Kind kind, Long itemId, Long roomId, boolean direct) {

  public LiveChange(LiveTopic topic, Kind kind, Long itemId, Long roomId) {
    this(topic, kind, itemId, roomId, false);
  }

  public enum Kind {
    REPLY,
    COMMENT,
    NOTE;

    public String apiName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
