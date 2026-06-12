package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.DeviceTokenEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaDeviceTokenRepository extends JpaRepository<DeviceTokenEntity, Long> {

  Optional<DeviceTokenEntity> findByToken(String token);

  @Transactional
  void deleteByToken(String token);

  @Query("select d.token from DeviceTokenEntity d where d.userId = :userId")
  List<String> tokensForUser(Long userId);

  @Query("select d.token from DeviceTokenEntity d where d.userId in :userIds")
  List<String> tokensForUsers(Collection<Long> userIds);
}
