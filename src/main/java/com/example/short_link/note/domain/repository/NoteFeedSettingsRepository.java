package com.example.short_link.note.domain.repository;

public interface NoteFeedSettingsRepository {

  boolean showsReposts(Long userId);

  void setShowsReposts(Long userId, boolean show);

  boolean hidesRepostsOf(Long userId, Long otherUserId);

  void hideRepostsOf(Long userId, Long otherUserId);

  void showRepostsOf(Long userId, Long otherUserId);
}
