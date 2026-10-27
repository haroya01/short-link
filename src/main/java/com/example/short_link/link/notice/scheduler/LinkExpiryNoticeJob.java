package com.example.short_link.link.notice.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.notification.LinkNoticeSender;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.notice.application.LinkNoticeProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Scans one daily expiry band, e.g. [now+6d, now+7d) for a seven-day threshold, so each link
// crosses the band once.
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkExpiryNoticeJob {

  private static final String LOCK_KEY = "notif:expiry";

  private final LinkRepository links;
  private final LinkNoticeSender notices;
  private final LinkNoticeProperties props;
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
        notices.send(
            new LinkNotice(
                link.getUserId(),
                LinkNoticeKind.EXPIRY_IMMINENT,
                link.getShortCode().value(),
                label,
                new LinkNoticeBody.Written("약 " + days + "일 뒤 만료돼요")));
        sent++;
      }
      log.info("link expiry notice: sent {} notifications", sent);
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
