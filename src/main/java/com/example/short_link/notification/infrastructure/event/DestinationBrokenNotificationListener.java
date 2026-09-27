package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.link.health.application.DestinationBrokenEvent;
import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationBrokenNotificationListener {

  private final LinkNotificationDispatcher dispatcher;
  private final NotificationUserReader users;
  private final MessageSource messages;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onDestinationBroken(DestinationBrokenEvent event) {
    try {
      dispatcher.dispatch(
          event.userId(),
          LinkNotificationType.DESTINATION_BROKEN,
          event.shortCode(),
          event.label(),
          body(event));
    } catch (Exception e) {
      log.warn("destination broken notification skipped: {}", e.toString());
    }
  }

  String body(DestinationBrokenEvent event) {
    Locale locale =
        Locale.forLanguageTag(
            users.findById(event.userId()).map(NotificationUser::locale).orElse("ko"));
    String key =
        event.failure() == DestinationFailure.NO_HOST
            ? "notification.link.destinationBroken.noHost"
            : "notification.link.destinationBroken.missing";
    String status = event.httpStatus() == null ? "" : String.valueOf(event.httpStatus());
    return messages.getMessage(key, new Object[] {status}, locale);
  }
}
