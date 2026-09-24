package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceTokenRepositoryAdapter implements DeviceTokenRepository {

  private final JpaDeviceTokenRepository jpa;

  @Override
  public Optional<DeviceTokenEntity> findByToken(String token) {
    return jpa.findByToken(token);
  }

  @Override
  public DeviceTokenEntity save(DeviceTokenEntity entity) {
    return jpa.save(entity);
  }

  @Override
  public void deleteByToken(String token) {
    jpa.deleteByToken(token);
  }

  @Override
  public List<DeviceTarget> targetsForUser(Long userId) {
    return jpa.targetsForUser(userId);
  }

  @Override
  public List<DeviceTarget> targetsForUsers(Collection<Long> userIds) {
    return userIds.isEmpty() ? List.of() : jpa.targetsForUsers(userIds);
  }

  @Override
  public void updateTopic(String token, String topic) {
    jpa.updateTopic(token, topic);
  }
}
