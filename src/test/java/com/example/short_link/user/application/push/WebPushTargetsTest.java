package com.example.short_link.user.application.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebPushTargetsTest {

  private final WebPushSubscriptionRepository subscriptions =
      mock(WebPushSubscriptionRepository.class);
  private final WebPushTargets targets = new WebPushTargets(subscriptions);

  @Test
  void aSubscriptionBecomesTheEndpointAndKeysTheBrowserGave() {
    when(subscriptions.findAllByUserId(1L))
        .thenReturn(
            List.of(new WebPushSubscriptionEntity(1L, "https://push.example/a", "pub", "secret")));
    when(subscriptions.findAllByUserIdIn(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                new WebPushSubscriptionEntity(1L, "https://push.example/a", "pub", "secret"),
                new WebPushSubscriptionEntity(2L, "https://push.example/b", "pub2", "secret2")));

    assertThat(targets.forUser(1L))
        .containsExactly(new WebPushTarget("https://push.example/a", "pub", "secret"));
    assertThat(targets.forUsers(List.of(1L, 2L)))
        .containsExactly(
            new WebPushTarget("https://push.example/a", "pub", "secret"),
            new WebPushTarget("https://push.example/b", "pub2", "secret2"));
  }

  @Test
  void forgettingAnEndpointDeletesItsSubscription() {
    targets.forget("https://push.example/a");

    verify(subscriptions).deleteByEndpoint("https://push.example/a");
  }
}
