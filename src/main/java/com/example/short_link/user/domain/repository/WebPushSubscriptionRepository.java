package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository {

  Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint);

  WebPushSubscriptionEntity save(WebPushSubscriptionEntity entity);

  /** 발송 실패(구독 만료/브라우저 해제)로 죽은 구독을 서버가 정리 — 소유자 무관, endpoint 로 단건 삭제. */
  void deleteByEndpoint(String endpoint);

  /** 사용자 요청 구독해제 — 자기 소유(userId) 의 endpoint 만 지운다. 남의 구독을 endpoint 만으로 못 지우게. */
  void deleteByUserIdAndEndpoint(Long userId, String endpoint);

  /** Purge every subscription a user owns — called on account hard delete (V99 has no users FK). */
  void deleteByUserId(Long userId);

  /** A user's subscriptions — the sender fans a push to every one (one per browser/device). */
  List<WebPushSubscriptionEntity> findAllByUserId(Long userId);

  List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds);
}
