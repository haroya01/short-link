package com.example.short_link.user.infrastructure.persistence;

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
  public List<String> tokensForUser(Long userId) {
    return jpa.tokensForUser(userId);
  }

  @Override
  public List<String> tokensForUsers(Collection<Long> userIds) {
    return userIds.isEmpty() ? List.of() : jpa.tokensForUsers(userIds);
  }
}
