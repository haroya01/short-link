package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.DeviceTokenEntity;
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
          + "from DeviceTokenEntity d where d.userId = :userId")
  List<DeviceTarget> targetsForUser(Long userId);

  @Query(
      "select new com.example.short_link.user.domain.DeviceTarget(d.token, d.topic) "
          + "from DeviceTokenEntity d where d.userId in :userIds")
  List<DeviceTarget> targetsForUsers(Collection<Long> userIds);

  @Transactional
  @Modifying
  @Query("update DeviceTokenEntity d set d.topic = :topic where d.token = :token")
  int updateTopic(String token, String topic);
}
