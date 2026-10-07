package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.PendingFollowRequest;
import java.util.List;

public interface FollowRequestRepository {

  boolean exists(Long followerId, Long followingId);

  FollowRequestEntity save(FollowRequestEntity request);

  int delete(Long followerId, Long followingId);

  List<PendingFollowRequest> pending(Long followingId, int offset, int limit);

  // Every waiting request becomes a follow — what unlocking an account does.
  int approveAll(Long followingId);
}
