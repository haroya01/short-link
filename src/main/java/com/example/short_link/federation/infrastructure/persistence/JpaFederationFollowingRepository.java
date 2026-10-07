package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFederationFollowingRepository
    extends JpaRepository<FederationFollowingEntity, Long> {

  Optional<FederationFollowingEntity> findByUserIdAndRemoteActorId(Long userId, Long remoteActorId);

  Optional<FederationFollowingEntity> findFirstByRemoteActorIdAndFollowActivityId(
      Long remoteActorId, String followActivityId);

  List<FederationFollowingEntity> findByUserIdOrderByIdDesc(Long userId, Pageable page);

  List<FederationFollowingEntity> findByUserId(Long userId);

  @Modifying
  @Query("delete from FederationFollowingEntity f where f.userId = :userId")
  int deleteAllForUser(@Param("userId") Long userId);
}
