package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.DeviceTokenEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

  Optional<DeviceTokenEntity> findByToken(String token);

  DeviceTokenEntity save(DeviceTokenEntity entity);

  void deleteByToken(String token);

  // Tokens whose login session is still live, or that were registered outside one.
  List<DeviceTarget> targetsForUser(Long userId, Instant now);

  List<DeviceTarget> targetsForUsers(Collection<Long> userIds, Instant now);

  void extendSession(Long userId, String sessionId, Instant expiresAt);

  void endSession(Long userId, String sessionId);

  void deleteByUserId(Long userId);

  void updateTopic(String token, String topic);
}
