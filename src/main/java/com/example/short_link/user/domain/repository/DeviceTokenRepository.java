package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.DeviceTokenEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

  Optional<DeviceTokenEntity> findByToken(String token);

  DeviceTokenEntity save(DeviceTokenEntity entity);

  void deleteByToken(String token);

  List<String> tokensForUser(Long userId);

  List<String> tokensForUsers(Collection<Long> userIds);
}
