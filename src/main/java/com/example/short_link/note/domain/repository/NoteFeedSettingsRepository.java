package com.example.short_link.note.domain.repository;

import java.util.List;

public interface NoteFeedSettingsRepository {

  record Preferences(boolean showReposts, List<String> languages) {}

  Preferences read(Long userId);

  void setShowsReposts(Long userId, boolean show);

  void setLanguages(Long userId, List<String> languages);

  boolean hidesRepostsOf(Long userId, Long otherUserId);

  void hideRepostsOf(Long userId, Long otherUserId);

  void showRepostsOf(Long userId, Long otherUserId);
}
