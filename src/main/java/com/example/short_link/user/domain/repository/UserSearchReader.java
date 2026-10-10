package com.example.short_link.user.domain.repository;

import java.time.Instant;
import java.util.List;

public interface UserSearchReader {

  record Match(
      Long userId,
      String username,
      String displayName,
      String avatarUrl,
      String bio,
      long followerCount,
      boolean followerCountHidden,
      boolean following,
      boolean requested) {}

  List<Match> search(Long viewerId, String query, Instant now, int offset, int limit);
}
