package com.example.short_link.federation.domain;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.List;

public record RemoteAccountMatch(
    Long id,
    String username,
    String domain,
    String displayName,
    String avatarUrl,
    String url,
    List<CustomEmoji> emojis,
    Relation relation) {

  public record Relation(boolean following, boolean requested, boolean muting) {

    public static final Relation NONE = new Relation(false, false, false);
  }
}
