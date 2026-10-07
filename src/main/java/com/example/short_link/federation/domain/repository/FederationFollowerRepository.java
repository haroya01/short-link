package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import java.util.List;
import java.util.Optional;

public interface FederationFollowerRepository {

  Optional<FederationFollowerEntity> find(Long userId, Long remoteActorId);

  FederationFollowerEntity save(FederationFollowerEntity follower);

  int delete(Long userId, Long remoteActorId);

  int deleteByFollowActivity(Long remoteActorId, String followActivityId);

  List<String> deliveryInboxes(Long userId);

  List<FollowOnDomain<FederationFollowerEntity>> onDomain(Long userId, String domain);

  // Follows from elsewhere waiting on a locked member's approval, newest first.
  List<FollowOnDomain<FederationFollowerEntity>> pending(Long userId);

  List<FollowOnDomain<FederationFollowerEntity>> pending(Long userId, int page, int size);

  int deleteAllForUser(Long userId);
}
