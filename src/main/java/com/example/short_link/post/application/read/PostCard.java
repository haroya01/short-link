package com.example.short_link.post.application.read;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record PostCard(
    @JsonUnwrapped PublicFeedItem item, int readMinutes, long commentCount, Boolean likedByMe) {

  static final int CHARS_PER_MINUTE = 500;

  static int readMinutes(int bodyTextLength) {
    return Math.max(1, Math.round(bodyTextLength / (float) CHARS_PER_MINUTE));
  }
}
