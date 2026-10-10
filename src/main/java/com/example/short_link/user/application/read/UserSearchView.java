package com.example.short_link.user.application.read;

import java.util.List;

public record UserSearchView(List<Item> items, int page, int size, boolean hasNext) {

  public record Item(
      Long userId,
      String username,
      String displayName,
      String avatarUrl,
      String bio,
      Long followerCount,
      boolean following,
      boolean requested) {}
}
