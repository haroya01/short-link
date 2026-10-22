package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
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

  private static final String EP = "https://fcm.googleapis.com/fcm/send/abc:APA91b";

  @Test
  void subscribeInsertsWhenEndpointUnknown() {
    when(subscriptions.findByEndpoint(EP)).thenReturn(Optional.empty());

    service.subscribe(1L, EP, "p256", "auth");

    ArgumentCaptor<WebPushSubscriptionEntity> saved =
        ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
    verify(subscriptions).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getEndpoint()).isEqualTo(EP);
    assertThat(saved.getValue().getP256dh()).isEqualTo("p256");
    assertThat(saved.getValue().getAuth()).isEqualTo("auth");
  }

  @Test
  void subscribeReassignsKnownEndpointToNewAccount() {
    WebPushSubscriptionEntity existing = new WebPushSubscriptionEntity(1L, EP, "p256", "auth");
    when(subscriptions.findByEndpoint(EP)).thenReturn(Optional.of(existing));

    service.subscribe(2L, EP, "p256", "auth");

    assertThat(existing.getUserId()).isEqualTo(2L);
    verify(subscriptions, never()).save(any());
  }

  @Test
  void subscribeAcceptsEveryBrowsersPushService() {
    for (String endpoint :
        List.of(
            EP,
            "https://updates.push.services.mozilla.com/wpush/v2/gAAAAAB",
            "https://wns2-par02p.notify.windows.com/w/?token=BQYAAAB",
            "https://web.push.apple.com/QGuQyavXutnMH",
            "https://FCM.googleapis.com:443/fcm/send/x")) {
      service.subscribe(1L, endpoint, "p256", "auth");
    }

    verify(subscriptions, times(5)).save(any());
  }

  @Test
  void subscribeRefusesAnythingButABrowserPushService() {
    for (String endpoint :
        List.of(
            "http://fcm.googleapis.com/fcm/send/x",
            "https://169.254.169.254/latest/meta-data",
            "https://localhost/push",
            "https://example.com/push",
            "https://fcm.googleapis.com.evil.example/x",
            "https://evilfcm.googleapis.com/x",
            "https://push.services.mozilla.com.evil.example/x",
            "https://user@fcm.googleapis.com/x",
            "https://fcm.googleapis.com:8443/x",
            "ftp://fcm.googleapis.com/x",
            "not a url")) {
      assertThatThrownBy(() -> service.subscribe(1L, endpoint, "p256", "auth"))
          .isInstanceOfSatisfying(
              UserException.class,
              e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.WEB_PUSH_ENDPOINT_INVALID));
    }

    verify(subscriptions, never()).findByEndpoint(any());
    verify(subscriptions, never()).save(any());
  }

  @Test
  void unsubscribeDeletesOnlyOwnEndpoint() {
    service.unsubscribe(7L, EP);

    // 자기 소유(userId=7)의 endpoint 만 지운다 — endpoint 만으로 남의 구독을 못 지우게.
    verify(subscriptions).deleteByUserIdAndEndpoint(7L, EP);
  }
}
