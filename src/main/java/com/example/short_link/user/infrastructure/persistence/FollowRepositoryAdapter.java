package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FollowRepositoryAdapter implements FollowRepository {

  private final JpaFollowRepository jpa;

  @Override
  public boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId) {
    return jpa.existsByFollowerIdAndFollowingId(followerId, followingId);
  }

  @Override
  public Optional<FollowEntity> findByFollowerIdAndFollowingId(Long followerId, Long followingId) {
    return jpa.findByFollowerIdAndFollowingId(followerId, followingId);
  }

  @Override
  public FollowEntity save(FollowEntity follow) {
    return jpa.save(follow);
  }

  @Override
  public void delete(FollowEntity follow) {
    jpa.delete(follow);
  }

  @Override
  public long countByFollowingId(Long followingId) {
    return jpa.countByFollowingId(followingId);
  }

  @Override
  public long countByFollowerId(Long followerId) {
    return jpa.countByFollowerId(followerId);
  }

  @Override
  public List<Long> findFollowingIds(Long followerId) {
    return jpa.findFollowingIds(followerId);
  }

  @Override
  public int deleteAllInvolving(Long userId) {
    return jpa.deleteAllInvolving(userId);
  }
}
