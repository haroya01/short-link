package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationFollowingRepositoryAdapter implements FederationFollowingRepository {

  private final JpaFederationFollowingRepository jpa;

  @Override
  public Optional<FederationFollowingEntity> find(Long userId, Long remoteActorId) {
    return jpa.findByUserIdAndRemoteActorId(userId, remoteActorId);
  }

  @Override
  public Optional<FederationFollowingEntity> findByFollowActivity(
      Long remoteActorId, String followActivityId) {
    return jpa.findFirstByRemoteActorIdAndFollowActivityId(remoteActorId, followActivityId);
  }

  @Override
  public FederationFollowingEntity save(FederationFollowingEntity following) {
    return jpa.save(following);
  }

  @Override
  public void delete(FederationFollowingEntity following) {
    jpa.delete(following);
  }

  @Override
  public List<FederationFollowingEntity> page(Long userId, int offset, int limit) {
    return jpa.findByUserIdOrderByIdDesc(userId, PageRequest.of(offset / limit, limit));
  }

  @Override
  public List<FederationFollowingEntity> allForUser(Long userId) {
    return jpa.findByUserId(userId);
  }

  @Override
  public List<FollowOnDomain<FederationFollowingEntity>> onDomain(Long userId, String domain) {
    return jpa.onDomain(userId, domain).stream()
        .map(
            row ->
                new FollowOnDomain<>(
                    (FederationFollowingEntity) row[0], (RemoteActorEntity) row[1]))
        .toList();
  }

  @Override
  public int deleteAllForUser(Long userId) {
    return jpa.deleteAllForUser(userId);
  }

  @Override
  public boolean anyAcceptedFollowOf(String actorUri) {
    return jpa.anyAcceptedFollowOf(actorUri);
  }
}
