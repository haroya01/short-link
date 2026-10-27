package com.example.short_link.user.application.push;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebPushTargets {

  private final WebPushSubscriptionRepository subscriptions;

  public List<WebPushTarget> forUser(Long userId) {
    return targets(subscriptions.findAllByUserId(userId));
  }

  public List<WebPushTarget> forUsers(Collection<Long> userIds) {
    return targets(subscriptions.findAllByUserIdIn(userIds));
  }

  public void forget(String endpoint) {
    subscriptions.deleteByEndpoint(endpoint);
  }

  private static List<WebPushTarget> targets(List<WebPushSubscriptionEntity> subscriptions) {
    return subscriptions.stream()
        .map(sub -> new WebPushTarget(sub.getEndpoint(), sub.getP256dh(), sub.getAuth()))
        .toList();
  }
}
