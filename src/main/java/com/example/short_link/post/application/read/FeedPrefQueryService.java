package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedPrefQueryService {

  private final UserFeedPrefRepository repository;

  /** The user's default feed tab, or {@link FeedPrefsView#DEFAULT} when they haven't set one. */
  public FeedPrefsView get(Long userId) {
    return repository
        .findByUserId(userId)
        .map(UserFeedPrefEntity::getDefaultTab)
        .filter(FeedPrefsView::isAllowed)
        .map(FeedPrefsView::new)
        .orElseGet(() -> new FeedPrefsView(FeedPrefsView.DEFAULT));
  }
}
