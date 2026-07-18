package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionCommandServiceTest {

  @Mock private WebPushSubscriptionRepository subscriptions;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private UserRepository userRepository;

  @InjectMocks private WebPushSubscriptionCommandService service;

  @Test
  void subscribeInsertsWhenEndpointUnknown() {
    when(subscriptions.findByEndpoint("ep-1")).thenReturn(Optional.empty());

    service.subscribe(1L, "ep-1", "p256", "auth");

    ArgumentCaptor<WebPushSubscriptionEntity> saved =
        ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
    verify(subscriptions).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getEndpoint()).isEqualTo("ep-1");
    assertThat(saved.getValue().getP256dh()).isEqualTo("p256");
    assertThat(saved.getValue().getAuth()).isEqualTo("auth");
  }

  @Test
  void subscribeReassignsKnownEndpointToNewAccount() {
    WebPushSubscriptionEntity existing = new WebPushSubscriptionEntity(1L, "ep-1", "p256", "auth");
    when(subscriptions.findByEndpoint("ep-1")).thenReturn(Optional.of(existing));

    service.subscribe(2L, "ep-1", "p256", "auth");

    assertThat(existing.getUserId()).isEqualTo(2L);
    verify(subscriptions, never()).save(any());
  }

  @Test
  void unsubscribeDeletesOnlyOwnEndpoint() {
    service.unsubscribe(7L, "ep-1");

    // 자기 소유(userId=7)의 endpoint 만 지운다 — endpoint 만으로 남의 구독을 못 지우게.
    verify(subscriptions).deleteByUserIdAndEndpoint(7L, "ep-1");
  }
}
