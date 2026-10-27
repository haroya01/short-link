package com.example.short_link.link.notice.application;

import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.notification.LinkNoticeSender;
import com.example.short_link.link.health.application.DestinationBrokenEvent;
import com.example.short_link.link.health.domain.DestinationFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationBrokenNoticeListener {

  private final LinkNoticeSender notices;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onDestinationBroken(DestinationBrokenEvent event) {
    try {
      notices.send(
          new LinkNotice(
              event.userId(),
              LinkNoticeKind.DESTINATION_BROKEN,
              event.shortCode(),
              event.label(),
              body(event)));
    } catch (Exception e) {
      log.warn("destination broken notification skipped: {}", e.toString());
    }
  }

  private static LinkNoticeBody body(DestinationBrokenEvent event) {
    String key =
        event.failure() == DestinationFailure.NO_HOST
            ? "notification.link.destinationBroken.noHost"
            : "notification.link.destinationBroken.missing";
    String status = event.httpStatus() == null ? "" : String.valueOf(event.httpStatus());
    return new LinkNoticeBody.Localized(key, status);
  }
}
