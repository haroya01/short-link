package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import java.util.Optional;

public interface FederationFollowerRepository {

  Optional<FederationFollowerEntity> find(Long userId, Long remoteActorId);

  FederationFollowerEntity save(FederationFollowerEntity follower);

  int delete(Long userId, Long remoteActorId);

  int deleteByFollowActivity(Long remoteActorId, String followActivityId);
}
