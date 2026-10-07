package com.example.short_link.user.domain.repository;

import java.time.Instant;
import java.util.List;

public interface FollowSuggestionReader {

  // FRIENDS: followed by people the member follows (mutuals counts them). POPULAR: much followed
  // and active lately, for a member who follows no one yet or has run out of friends' picks.
  enum Reason {
    FRIENDS,
    POPULAR
  }

  record Suggestion(
      String username,
      String displayName,
      String avatarUrl,
      String bio,
      long mutuals,
      Reason reason,
      boolean locked) {}

  List<Suggestion> suggestions(Long userId, Instant activeSince, int limit);

  void dismiss(Long userId, Long dismissedId);
}
