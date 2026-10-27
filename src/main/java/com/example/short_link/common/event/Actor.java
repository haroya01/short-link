package com.example.short_link.common.event;

// Who caused something: a member here, an account on another server, or nobody.
public record Actor(Long userId, Long remoteId) {

  public static final Actor NONE = new Actor(null, null);

  public Actor {
    if (userId != null && remoteId != null) {
      throw new IllegalArgumentException("An actor is a member or a remote account, not both");
    }
  }

  public static Actor member(Long userId) {
    return new Actor(userId, null);
  }

  public static Actor remote(Long remoteId) {
    return new Actor(null, remoteId);
  }

  public boolean isNone() {
    return userId == null && remoteId == null;
  }
}
