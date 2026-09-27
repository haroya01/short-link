package com.example.short_link.notification.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.health.application.DestinationBrokenEvent;
import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class DestinationBrokenNotificationListenerTest {

  private final LinkNotificationDispatcher dispatcher = mock(LinkNotificationDispatcher.class);
  private final NotificationUserReader users = mock(NotificationUserReader.class);
  private final DestinationBrokenNotificationListener listener =
      new DestinationBrokenNotificationListener(dispatcher, users, messages());

  private static ResourceBundleMessageSource messages() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  @Test
  void theOwnerIsToldInTheirOwnLanguage() {
    when(users.findById(42L)).thenReturn(Optional.of(new NotificationUser(42L, "hana", "ja")));

    listener.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "春セール", DestinationFailure.NOT_FOUND, 404));

    verify(dispatcher)
        .dispatch(
            42L,
            LinkNotificationType.DESTINATION_BROKEN,
            "abc1234",
            "春セール",
            "リンク先のページがなくなりました（404）。訪問者にはエラーページが表示されています。新しいアドレスに変更してください。");
  }

  @Test
  void aVanishedDomainGetsItsOwnWords() {
    when(users.findById(42L)).thenReturn(Optional.empty());

    listener.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "/abc1234", DestinationFailure.NO_HOST, null));

    verify(dispatcher)
        .dispatch(
            42L,
            LinkNotificationType.DESTINATION_BROKEN,
            "abc1234",
            "/abc1234",
            "목적지 도메인을 찾을 수 없어요. 주소를 확인해 주세요.");
  }
}
