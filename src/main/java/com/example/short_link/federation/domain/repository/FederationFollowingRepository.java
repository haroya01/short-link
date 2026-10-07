package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import java.util.List;
import java.util.Optional;

public interface FederationFollowingRepository {

  Optional<FederationFollowingEntity> find(Long userId, Long remoteActorId);

  Optional<FederationFollowingEntity> findByFollowActivity(
      Long remoteActorId, String followActivityId);

  FederationFollowingEntity save(FederationFollowingEntity following);

  void delete(FederationFollowingEntity following);

  List<FederationFollowingEntity> page(Long userId, int offset, int limit);

  List<FederationFollowingEntity> allForUser(Long userId);

  List<FollowOnDomain<FederationFollowingEntity>> onDomain(Long userId, String domain);

  int deleteAllForUser(Long userId);

  boolean anyAcceptedFollowOf(String actorUri);
}
