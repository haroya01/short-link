package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FollowRepository {

  boolean exists(FollowEdge edge);

  Optional<FollowEntity> find(FollowEdge edge);

  FollowEntity save(FollowEntity follow);

  void delete(FollowEntity follow);

  long countByFollowingId(Long followingId);

  long countByFollowerId(Long followerId);

  List<Long> findFollowingIds(Long followerId);

  List<Long> findFollowerIds(Long followingId, int page, int size);

  List<Long> findFollowingIds(Long followerId, int page, int size);

  List<Long> findFollowedAmong(Long viewerId, Collection<Long> candidateIds);

  Map<Long, Long> countFollowersByIdIn(Collection<Long> followingIds);

  int deleteAllInvolving(Long userId);

  int deleteBetween(Long a, Long b);
}
