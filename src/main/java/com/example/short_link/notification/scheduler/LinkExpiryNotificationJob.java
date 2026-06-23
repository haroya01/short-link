package com.example.short_link.notification.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.application.link.LinkNotificationProperties;
import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "Your link expires soon." Fires once per link by scanning a single one-day band at the threshold
 * (e.g. [now+6d, now+7d) for a 7-day threshold) each daily run, so a link crosses it exactly once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkExpiryNotificationJob {

  private static final String LOCK_KEY = "notif:expiry";

  private final LinkRepository links;
  private final LinkNotificationDispatcher dispatcher;
  private final LinkNotificationProperties props;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.link-notification.expiry-cron:0 0 9 * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!props.expiryEnabled()) {
      return;
    }
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(10))) {
      return;
    }
    try {
      int days = props.expiryThresholdDays();
      Instant now = Instant.now();
      Instant from = now.plus(Duration.ofDays(days - 1L));
      Instant to = now.plus(Duration.ofDays(days));
      int sent = 0;
      for (LinkEntity link : links.findByExpiresAtBetween(from, to)) {
        if (link.getUserId() == null) {
          continue;
        }
        String label =
            link.getNote() != null && !link.getNote().isBlank()
                ? link.getNote()
                : "/" + link.getShortCode();
        dispatcher.dispatch(
            link.getUserId(),
            LinkNotificationType.EXPIRY_IMMINENT,
            label,
            "약 " + days + "일 뒤 만료돼요");
        sent++;
      }
      log.info("link expiry notice: sent {} notifications", sent);
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
