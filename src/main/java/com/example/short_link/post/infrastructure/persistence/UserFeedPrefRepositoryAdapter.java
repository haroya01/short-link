package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserFeedPrefRepositoryAdapter implements UserFeedPrefRepository {

  private final JpaUserFeedPrefRepository jpa;

  @Override
  public Optional<UserFeedPrefEntity> findByUserId(Long userId) {
    return jpa.findByUserId(userId);
  }

  @Override
  public UserFeedPrefEntity save(UserFeedPrefEntity pref) {
    return jpa.save(pref);
  }
}
