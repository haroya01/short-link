package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository {

  Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint);

  WebPushSubscriptionEntity save(WebPushSubscriptionEntity entity);

  void deleteByEndpoint(String endpoint);

  /** A user's subscriptions — the sender fans a push to every one (one per browser/device). */
  List<WebPushSubscriptionEntity> findAllByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds);
}
