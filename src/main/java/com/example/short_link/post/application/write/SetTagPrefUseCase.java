package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Set a user's tag preference. FOLLOW and HIDE are mutually exclusive per tag (one row), so setting
 * one flips the row's kind. Remove deletes the row only when it currently holds that kind, so
 * "unfollow" can't clear a HIDE and vice versa. All operations are idempotent.
 */
@Service
@RequiredArgsConstructor
public class SetTagPrefUseCase {

  private static final int MAX_TAG = 40;

  private final UserTagPrefRepository repository;

  @Transactional
  public void follow(Long userId, String tag) {
    upsert(userId, tag, TagPrefKind.FOLLOW);
  }

  @Transactional
  public void hide(Long userId, String tag) {
    upsert(userId, tag, TagPrefKind.HIDE);
  }

  @Transactional
  public void unfollow(Long userId, String tag) {
    removeIfKind(userId, tag, TagPrefKind.FOLLOW);
  }

  @Transactional
  public void unhide(Long userId, String tag) {
    removeIfKind(userId, tag, TagPrefKind.HIDE);
  }

  private void upsert(Long userId, String rawTag, TagPrefKind kind) {
    String tag = normalize(rawTag);
    if (tag.isEmpty()) return;
    repository
        .findByUserIdAndTag(userId, tag)
        .ifPresentOrElse(
            pref -> {
              pref.changeKind(kind);
              repository.save(pref);
            },
            () -> repository.save(new UserTagPrefEntity(userId, tag, kind)));
  }

  private void removeIfKind(Long userId, String rawTag, TagPrefKind kind) {
    String tag = normalize(rawTag);
    repository
        .findByUserIdAndTag(userId, tag)
        .filter(pref -> pref.getKind() == kind)
        .ifPresent(repository::delete);
  }

  private static String normalize(String tag) {
    if (tag == null) return "";
    String t = tag.trim();
    return t.length() > MAX_TAG ? t.substring(0, MAX_TAG).trim() : t;
  }
}
