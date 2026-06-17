package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface JpaWebPushSubscriptionRepository
    extends JpaRepository<WebPushSubscriptionEntity, Long> {

  Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint);

  @Transactional
  void deleteByEndpoint(String endpoint);

  List<WebPushSubscriptionEntity> findAllByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds);
}
