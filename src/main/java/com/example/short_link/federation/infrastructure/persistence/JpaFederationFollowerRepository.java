package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFederationFollowerRepository
    extends JpaRepository<FederationFollowerEntity, Long> {

  Optional<FederationFollowerEntity> findByUserIdAndRemoteActorId(Long userId, Long remoteActorId);

  @Modifying
  @Query(
      "delete from FederationFollowerEntity f"
          + " where f.userId = :userId and f.remoteActorId = :remoteActorId")
  int deleteFollow(@Param("userId") Long userId, @Param("remoteActorId") Long remoteActorId);

  @Modifying
  @Query(
      "delete from FederationFollowerEntity f where f.remoteActorId = :remoteActorId"
          + " and f.followActivityId = :followActivityId")
  int deleteFollowActivity(
      @Param("remoteActorId") Long remoteActorId,
      @Param("followActivityId") String followActivityId);
}
