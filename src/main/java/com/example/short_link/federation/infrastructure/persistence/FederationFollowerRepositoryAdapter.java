package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationFollowerRepositoryAdapter implements FederationFollowerRepository {

  private final JpaFederationFollowerRepository jpa;

  @Override
  public Optional<FederationFollowerEntity> find(Long userId, Long remoteActorId) {
    return jpa.findByUserIdAndRemoteActorId(userId, remoteActorId);
  }

  @Override
  public FederationFollowerEntity save(FederationFollowerEntity follower) {
    return jpa.save(follower);
  }

  @Override
  public int delete(Long userId, Long remoteActorId) {
    return jpa.deleteFollow(userId, remoteActorId);
  }

  @Override
  public int deleteByFollowActivity(Long remoteActorId, String followActivityId) {
    return jpa.deleteFollowActivity(remoteActorId, followActivityId);
  }
}
