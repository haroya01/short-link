package com.example.short_link.link.notice.application;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.notification.LinkNoticeSender;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.Set;
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
public class LinkClickNoticeListener {

  private static final Set<Long> MILESTONES = Set.of(10L, 100L, 1000L, 10_000L, 100_000L);
  private static final String VELOCITY_COOLDOWN = "notif:velocity:";

  private final LinkRepository links;
  private final ClickTotalsReadRepository totals;
  private final ClickRangeReadRepository ranges;
  private final LinkNoticeSender notices;
  private final LinkNoticeProperties props;
  private final RedisDistributedLock cooldown;

  // NOT_SUPPORTED로 조회별 연결을 반환해 느린 푸시 I/O 동안 JDBC 연결을 점유하지 않는다.
  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onClickRecorded(ClickRecordedEvent event) {
    if (event.bot()) {
      return;
    }
    try {
      Long linkId = event.linkId().value();
      LinkEntity link = links.findById(linkId).orElse(null);
      if (link == null || link.getUserId() == null) {
        return;
      }
      long human = totals.countHumanByLinkId(linkId);

      if (human == 1L) {
        notices.send(notice(link, LinkNoticeKind.FIRST_CLICK, "첫 클릭이 들어왔어요 🎉"));
        return; // 첫 클릭이면 마일스톤/급증은 무의미
      }
      if (MILESTONES.contains(human)) {
        notices.send(notice(link, LinkNoticeKind.MILESTONE, human + " 클릭을 넘었어요"));
      }
      maybeVelocity(link, linkId, human, event.occurredAt());
    } catch (Exception e) {
      log.warn("link click notification skipped: {}", e.toString());
    }
  }

  private void maybeVelocity(LinkEntity link, Long linkId, long human, Instant now) {
    ClickSpike spike = new ClickSpike(props.velocityMinClicks(), props.velocityRatio());
    if (!spike.considers(human)) {
      return;
    }
    Instant lastHour = now.minus(Duration.ofHours(1));
    long current = ranges.countHumanByLinkIdAndRange(linkId, lastHour, now);
    if (!spike.busy(current)) {
      return;
    }
    long prior =
        ranges.countHumanByLinkIdAndRange(linkId, now.minus(Duration.ofHours(25)), lastHour);
    OptionalLong timesUsual = spike.timesUsual(current, prior);
    if (timesUsual.isEmpty()) {
      return;
    }
    // 12시간 쿨다운 — 락을 잡고 풀지 않아 TTL 로 만료(같은 링크 급증 도배 방지).
    if (!cooldown.tryAcquire(VELOCITY_COOLDOWN + linkId, Duration.ofHours(12))) {
      return;
    }
    notices.send(
        notice(
            link,
            LinkNoticeKind.VELOCITY_SPIKE,
            "지금 평소의 " + timesUsual.getAsLong() + "배 — 어디서 퍼지는지 보세요"));
  }

  private static LinkNotice notice(LinkEntity link, LinkNoticeKind kind, String text) {
    return new LinkNotice(
        link.getUserId(),
        kind,
        link.getShortCode().value(),
        label(link),
        new LinkNoticeBody.Written(text));
  }

  private static String label(LinkEntity link) {
    String note = link.getNote();
    if (note != null && !note.isBlank()) {
      return note;
    }
    return "/" + link.getShortCode();
  }
}
