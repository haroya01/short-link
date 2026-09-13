package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.FollowEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FollowRepository {

  boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

  Optional<FollowEntity> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

  FollowEntity save(FollowEntity follow);

  void delete(FollowEntity follow);

  long countByFollowingId(Long followingId);

  long countByFollowerId(Long followerId);

  List<Long> findFollowingIds(Long followerId);

  /** Newest follower edge first. */
  List<Long> findFollowerIds(Long followingId, int page, int size);

  /** Newest following edge first. */
  List<Long> findFollowingIds(Long followerId, int page, int size);

  List<Long> findFollowedAmong(Long viewerId, Collection<Long> candidateIds);

  /** Maps followingId to follower count in one batch. */
  Map<Long, Long> countFollowersByIdIn(Collection<Long> followingIds);

  /** Removes edges where the user appears on either side. */
  int deleteAllInvolving(Long userId);
}
