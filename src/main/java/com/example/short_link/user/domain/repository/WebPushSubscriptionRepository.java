package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository {

  Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint);

  WebPushSubscriptionEntity save(WebPushSubscriptionEntity entity);

  void deleteByEndpoint(String endpoint);

  // endpoint 만으로는 남의 구독을 지우지 못한다.
  void deleteByUserIdAndEndpoint(Long userId, String endpoint);

  // Must be called on account hard delete; subscriptions have no users FK.
  void deleteByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds);
}
