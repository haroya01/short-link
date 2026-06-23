package com.example.short_link.notification.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.application.link.LinkNotificationProperties;
import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One calm daily digest per active owner — yesterday's human clicks + top link. Skips owners with
 * no clicks (no empty pings). Each user is read via per-repo transactions (no loop-held
 * connection).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkDigestJob {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String LOCK_KEY = "notif:digest";

  private final LinkRepository links;
  private final ClickRangeReadRepository ranges;
  private final LinkNotificationDispatcher dispatcher;
  private final LinkNotificationProperties props;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.link-notification.digest-cron:0 0 8 * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!props.digestEnabled()) {
      return;
    }
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(10))) {
      return;
    }
    try {
      LocalDate today = LocalDate.now(KST);
      Instant from = today.minusDays(1).atStartOfDay(KST).toInstant();
      Instant to = today.atStartOfDay(KST).toInstant();
      int sent = 0;
      for (Long userId : links.findDistinctUserIds()) {
        if (userId == null) {
          continue;
        }
        long human = ranges.countHumanByUserIdAndRange(userId, from, to);
        if (human == 0) {
          continue;
        }
        dispatcher.dispatch(
            userId, LinkNotificationType.DIGEST, "어제 요약", body(userId, human, from, to));
        sent++;
      }
      log.info("link digest: sent {} notifications", sent);
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  private String body(Long userId, long human, Instant from, Instant to) {
    String base = "어제 클릭 " + human + "회";
    List<LinkClickCount> top = ranges.findTopLinksByUserIdAndRange(userId, from, to, 1);
    if (top.isEmpty()) {
      return base;
    }
    LinkEntity link = links.findById(top.get(0).getLinkId()).orElse(null);
    if (link == null) {
      return base;
    }
    String label =
        link.getNote() != null && !link.getNote().isBlank()
            ? link.getNote()
            : "/" + link.getShortCode();
    return base + " · 톱 " + label;
  }
}
