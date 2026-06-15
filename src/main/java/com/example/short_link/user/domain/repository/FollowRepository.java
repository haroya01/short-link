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

  /** How many users follow this user (their follower count). */
  long countByFollowingId(Long followingId);

  /** How many users this user follows (their following count). */
  long countByFollowerId(Long followerId);

  /** Ids of everyone this user follows — drives the "following" feed. */
  List<Long> findFollowingIds(Long followerId);

  /** A page of follower ids (users who follow this user), newest edge first. */
  List<Long> findFollowerIds(Long followingId, int page, int size);

  /** A page of following ids (users this user follows), newest edge first. */
  List<Long> findFollowingIds(Long followerId, int page, int size);

  /** Of the given candidates, the subset the viewer already follows — per-row follow state. */
  List<Long> findFollowedAmong(Long viewerId, Collection<Long> candidateIds);

  /** Follower counts for a set of users in one pass ({@code followingId -> count}). */
  Map<Long, Long> countFollowersByIdIn(Collection<Long> followingIds);

  /** Removes every edge the user appears on (either side) — account hard-delete path. */
  int deleteAllInvolving(Long userId);
}
