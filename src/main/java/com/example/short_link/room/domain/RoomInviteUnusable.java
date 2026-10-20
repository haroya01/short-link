package com.example.short_link.room.domain;

import java.util.Locale;

public enum RoomInviteUnusable {
  DISABLED,
  EXPIRED,
  USED_UP,
  FULL;

  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
