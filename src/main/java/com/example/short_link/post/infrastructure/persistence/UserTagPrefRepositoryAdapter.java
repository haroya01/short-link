package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserTagPrefRepositoryAdapter implements UserTagPrefRepository {

  private final JpaUserTagPrefRepository jpa;

  @Override
  public List<UserTagPrefEntity> findAllByUserId(Long userId) {
    return jpa.findAllByUserId(userId);
  }

  @Override
  public Optional<UserTagPrefEntity> findByUserIdAndTag(Long userId, String tag) {
    return jpa.findByUserIdAndTag(userId, tag);
  }

  @Override
  public UserTagPrefEntity save(UserTagPrefEntity pref) {
    return jpa.save(pref);
  }

  @Override
  public void delete(UserTagPrefEntity pref) {
    jpa.delete(pref);
  }
}
