package com.example.short_link.notification.application.link;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class LinkNoticeDeliveryTest {

  private final LinkNotificationDispatcher dispatcher = mock(LinkNotificationDispatcher.class);
  private final NotificationUserReader users = mock(NotificationUserReader.class);
  private final LinkNoticeDelivery delivery = new LinkNoticeDelivery(dispatcher, users, messages());

  private static ResourceBundleMessageSource messages() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  @Test
  void aWrittenNoticeGoesOutAsLinkWroteItWithoutReadingTheOwner() {
    delivery.send(
        new LinkNotice(
            5L,
            LinkNoticeKind.FIRST_CLICK,
            "spring",
            "/spring",
            new LinkNoticeBody.Written("첫 클릭이 들어왔어요 🎉")));

    verify(dispatcher)
        .dispatch(5L, LinkNotificationType.FIRST_CLICK, "spring", "/spring", "첫 클릭이 들어왔어요 🎉");
    verifyNoInteractions(users);
  }

  @Test
  void theOwnerIsToldInTheirOwnLanguage() {
    when(users.findById(42L)).thenReturn(Optional.of(new NotificationUser(42L, "hana", "ja")));

    delivery.send(
        new LinkNotice(
            42L,
            LinkNoticeKind.DESTINATION_BROKEN,
            "abc1234",
            "春セール",
            new LinkNoticeBody.Localized("notification.link.destinationBroken.missing", "404")));

    verify(dispatcher)
        .dispatch(
            42L,
            LinkNotificationType.DESTINATION_BROKEN,
            "abc1234",
            "春セール",
            "リンク先のページがなくなりました（404）。訪問者にはエラーページが表示されています。新しいアドレスに変更してください。");
  }

  @Test
  void anOwnerWithoutALanguageReadsKorean() {
    when(users.findById(42L)).thenReturn(Optional.empty());

    delivery.send(
        new LinkNotice(
            42L,
            LinkNoticeKind.DESTINATION_BROKEN,
            "abc1234",
            "/abc1234",
            new LinkNoticeBody.Localized("notification.link.destinationBroken.noHost", "")));

    verify(dispatcher)
        .dispatch(
            42L,
            LinkNotificationType.DESTINATION_BROKEN,
            "abc1234",
            "/abc1234",
            "목적지 도메인을 찾을 수 없어요. 주소를 확인해 주세요.");
  }

  @Test
  void everyKindKeepsItsStoredTypeName() {
    for (LinkNoticeKind kind : LinkNoticeKind.values()) {
      delivery.send(new LinkNotice(5L, kind, null, "l", new LinkNoticeBody.Written("b")));

      verify(dispatcher).dispatch(5L, LinkNotificationType.valueOf(kind.name()), null, "l", "b");
    }
  }
}
