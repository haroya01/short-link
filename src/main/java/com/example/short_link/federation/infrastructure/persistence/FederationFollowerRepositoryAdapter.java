package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  @Override
  public List<String> deliveryInboxes(Long userId) {
    return jpa.deliveryInboxes(userId);
  }

  @Override
  public List<FollowOnDomain<FederationFollowerEntity>> onDomain(Long userId, String domain) {
    return jpa.onDomain(userId, domain).stream()
        .map(
            row ->
                new FollowOnDomain<>((FederationFollowerEntity) row[0], (RemoteActorEntity) row[1]))
        .toList();
  }

  @Override
  public List<FollowOnDomain<FederationFollowerEntity>> pending(Long userId) {
    return pairs(jpa.pending(userId, Pageable.unpaged()));
  }

  @Override
  public List<FollowOnDomain<FederationFollowerEntity>> pending(Long userId, int page, int size) {
    return pairs(jpa.pending(userId, PageRequest.of(page, size)));
  }

  private static List<FollowOnDomain<FederationFollowerEntity>> pairs(List<Object[]> rows) {
    return rows.stream()
        .map(
            row ->
                new FollowOnDomain<>((FederationFollowerEntity) row[0], (RemoteActorEntity) row[1]))
        .toList();
  }

  @Override
  public int deleteAllForUser(Long userId) {
    return jpa.deleteAllForUser(userId);
  }
}
