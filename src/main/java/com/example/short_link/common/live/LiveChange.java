package com.example.short_link.common.live;

import java.util.Locale;

public record LiveChange(LiveTopic topic, Kind kind, Long itemId, Long roomId) {

  public enum Kind {
    REPLY,
    COMMENT,
    NOTE;

    public String apiName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
