package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository {

  Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint);

  WebPushSubscriptionEntity save(WebPushSubscriptionEntity entity);

  void deleteByEndpoint(String endpoint);

  // 사용자 요청 구독해제 — 자기 소유(userId) 의 endpoint 만 지운다. 남의 구독을 endpoint 만으로 못 지우게.
  void deleteByUserIdAndEndpoint(Long userId, String endpoint);

  // Must be called on account hard delete; subscriptions have no users FK.
  void deleteByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds);
}
