package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.DeviceTokenEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

  Optional<DeviceTokenEntity> findByToken(String token);

  DeviceTokenEntity save(DeviceTokenEntity entity);

  void deleteByToken(String token);

  List<DeviceTarget> targetsForUser(Long userId);

  List<DeviceTarget> targetsForUsers(Collection<Long> userIds);

  void updateTopic(String token, String topic);
}
