package com.example.short_link.common.live;

import java.util.Locale;

public record LiveTopic(Scope scope, Long id) {

  public enum Scope {
    NOTE,
    POST,
    ROOM;

    public String apiName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static LiveTopic note(Long id) {
    return new LiveTopic(Scope.NOTE, id);
  }

  public static LiveTopic post(Long id) {
    return new LiveTopic(Scope.POST, id);
  }

  public static LiveTopic room(Long id) {
    return new LiveTopic(Scope.ROOM, id);
  }

  public String tokenScope() {
    return "live:" + scope.apiName() + ":" + id;
  }
}
