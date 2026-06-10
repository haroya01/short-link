package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.FollowEntity;
import java.util.List;
import java.util.Optional;

public interface FollowRepository {

  boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

  Optional<FollowEntity> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

  FollowEntity save(FollowEntity follow);

  void delete(FollowEntity follow);

  /** How many users follow this user (their follower count). */
  long countByFollowingId(Long followingId);

  /** How many users this user follows (their following count). */
  long countByFollowerId(Long followerId);

  /** Ids of everyone this user follows — drives the "following" feed. */
  List<Long> findFollowingIds(Long followerId);

  /** Removes every edge the user appears on (either side) — account hard-delete path. */
  int deleteAllInvolving(Long userId);
}
