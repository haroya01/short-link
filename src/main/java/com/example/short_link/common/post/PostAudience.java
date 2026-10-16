package com.example.short_link.common.post;

public enum PostAudience {
  PUBLIC,
  UNLISTED,
  FOLLOWERS,
  PASSWORD;

  public PostLock lock() {
    return switch (this) {
      case FOLLOWERS -> PostLock.FOLLOWERS;
      case PASSWORD -> PostLock.PASSWORD;
      case PUBLIC, UNLISTED -> null;
    };
  }
}
