package com.example.short_link.link.notice.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.notification.LinkNoticeSender;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.notice.application.LinkNoticeProperties;
import com.example.short_link.link.stats.application.read.ClickRangeReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Skips owners with no human clicks. Repository-scoped transactions release the connection between
// owners.
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkDigestJob {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String LOCK_KEY = "notif:digest";

  private final LinkRepository links;
  private final ClickRangeReader ranges;
  private final LinkNoticeSender notices;
  private final LinkNoticeProperties props;
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
        notices.send(
            new LinkNotice(
                userId,
                LinkNoticeKind.DIGEST,
                null,
                "어제 요약",
                new LinkNoticeBody.Written(body(userId, human, from, to))));
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
