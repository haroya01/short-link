package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.PendingFollowRequest;
import java.util.List;

public interface FollowRequestRepository {

  boolean exists(FollowEdge edge);

  FollowRequestEntity save(FollowRequestEntity request);

  int delete(FollowEdge edge);

  List<PendingFollowRequest> pending(Long followingId, int offset, int limit);

  // Turns every waiting request into a follow, as unlocking the account does.
  int approveAll(Long followingId);
}
