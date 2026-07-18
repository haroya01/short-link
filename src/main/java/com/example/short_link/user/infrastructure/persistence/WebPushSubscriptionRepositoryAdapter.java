package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WebPushSubscriptionRepositoryAdapter implements WebPushSubscriptionRepository {

  private final JpaWebPushSubscriptionRepository jpa;

  @Override
  public Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint) {
    return jpa.findByEndpoint(endpoint);
  }

  @Override
  public WebPushSubscriptionEntity save(WebPushSubscriptionEntity entity) {
    return jpa.save(entity);
  }

  @Override
  public void deleteByEndpoint(String endpoint) {
    jpa.deleteByEndpoint(endpoint);
  }

  @Override
  public void deleteByUserIdAndEndpoint(Long userId, String endpoint) {
    jpa.deleteByUserIdAndEndpoint(userId, endpoint);
  }

  @Override
  public void deleteByUserId(Long userId) {
    jpa.deleteByUserId(userId);
  }

  @Override
  public List<WebPushSubscriptionEntity> findAllByUserId(Long userId) {
    return jpa.findAllByUserId(userId);
  }

  @Override
  public List<WebPushSubscriptionEntity> findAllByUserIdIn(Collection<Long> userIds) {
    return userIds.isEmpty() ? List.of() : jpa.findAllByUserIdIn(userIds);
  }
}
