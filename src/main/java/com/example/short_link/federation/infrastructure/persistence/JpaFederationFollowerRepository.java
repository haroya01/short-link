package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

  @Query(
      "select distinct coalesce(r.sharedInbox, r.inbox) from FederationFollowerEntity f,"
          + " RemoteActorEntity r where r.id = f.remoteActorId and f.userId = :userId"
          + " and f.acceptedAt is not null")
  List<String> deliveryInboxes(@Param("userId") Long userId);

  @Query(
      "select f, r from FederationFollowerEntity f, RemoteActorEntity r"
          + " where r.id = f.remoteActorId and f.userId = :userId and r.domain = :domain")
  List<Object[]> onDomain(@Param("userId") Long userId, @Param("domain") String domain);

  @Query(
      "select f, r from FederationFollowerEntity f, RemoteActorEntity r"
          + " where r.id = f.remoteActorId and f.userId = :userId and f.acceptedAt is null"
          + " order by f.id desc")
  List<Object[]> pending(@Param("userId") Long userId, Pageable page);

  @Modifying
  @Query("delete from FederationFollowerEntity f where f.userId = :userId")
  int deleteAllForUser(@Param("userId") Long userId);
}
