package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.DeviceTokenEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaDeviceTokenRepository extends JpaRepository<DeviceTokenEntity, Long> {

  Optional<DeviceTokenEntity> findByToken(String token);

  @Transactional
  void deleteByToken(String token);

  @Query(
      "select new com.example.short_link.user.domain.DeviceTarget(d.token, d.topic) "
          + "from DeviceTokenEntity d where d.userId = :userId "
          + "and (d.sessionExpiresAt is null or d.sessionExpiresAt > :now)")
  List<DeviceTarget> targetsForUser(Long userId, Instant now);

  @Query(
      "select new com.example.short_link.user.domain.DeviceTarget(d.token, d.topic) "
          + "from DeviceTokenEntity d where d.userId in :userIds "
          + "and (d.sessionExpiresAt is null or d.sessionExpiresAt > :now)")
  List<DeviceTarget> targetsForUsers(Collection<Long> userIds, Instant now);

  @Transactional
  @Modifying
  @Query(
      "update DeviceTokenEntity d set d.sessionExpiresAt = :expiresAt "
          + "where d.userId = :userId and d.sessionId = :sessionId")
  int extendSession(Long userId, String sessionId, Instant expiresAt);

  @Transactional
  @Modifying
  @Query("delete from DeviceTokenEntity d where d.userId = :userId and d.sessionId = :sessionId")
  int endSession(Long userId, String sessionId);

  @Transactional
  @Modifying
  @Query("delete from DeviceTokenEntity d where d.userId = :userId")
  int deleteByUserId(Long userId);

  @Transactional
  @Modifying
  @Query("update DeviceTokenEntity d set d.topic = :topic where d.token = :token")
  int updateTopic(String token, String topic);
}
