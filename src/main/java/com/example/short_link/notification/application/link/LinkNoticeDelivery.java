package com.example.short_link.notification.application.link;

import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.notification.LinkNoticeSender;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkNoticeDelivery implements LinkNoticeSender {

  private final LinkNotificationDispatcher dispatcher;
  private final NotificationUserReader users;
  private final MessageSource messages;

  @Override
  public void send(LinkNotice notice) {
    dispatcher.dispatch(
        notice.ownerUserId(),
        type(notice.kind()),
        notice.shortCode(),
        notice.label(),
        body(notice));
  }

  private String body(LinkNotice notice) {
    return switch (notice.body()) {
      case LinkNoticeBody.Written written -> written.text();
      case LinkNoticeBody.Localized localized ->
          messages.getMessage(
              localized.messageKey(),
              new Object[] {localized.argument()},
              localeOf(notice.ownerUserId()));
    };
  }

  private Locale localeOf(Long userId) {
    return Locale.forLanguageTag(users.findById(userId).map(NotificationUser::locale).orElse("ko"));
  }

  private static LinkNotificationType type(LinkNoticeKind kind) {
    return switch (kind) {
      case FIRST_CLICK -> LinkNotificationType.FIRST_CLICK;
      case MILESTONE -> LinkNotificationType.MILESTONE;
      case VELOCITY_SPIKE -> LinkNotificationType.VELOCITY_SPIKE;
      case EXPIRY_IMMINENT -> LinkNotificationType.EXPIRY_IMMINENT;
      case DIGEST -> LinkNotificationType.DIGEST;
      case DESTINATION_BROKEN -> LinkNotificationType.DESTINATION_BROKEN;
    };
  }
}
