package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.application.link.LinkNotificationProperties;
import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owner notifications driven by a human click: first click, round milestones, velocity spikes. Runs
 * after the click transaction commits, off-request on the webhook pool — never on the redirect hot
 * path. Velocity is cooled down 12h per link via Redis so a sustained spike isn't spammed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkClickNotificationListener {

  private static final Set<Long> MILESTONES = Set.of(10L, 100L, 1000L, 10_000L, 100_000L);
  private static final String VELOCITY_COOLDOWN = "notif:velocity:";

  private final LinkRepository links;
  private final ClickTotalsReadRepository totals;
  private final ClickRangeReadRepository ranges;
  private final LinkNotificationDispatcher dispatcher;
  private final LinkNotificationProperties props;
  private final RedisDistributedLock cooldown;

  // AFTER_COMMIT 이라 원래 클릭 트랜잭션은 이미 끝났다. 여기서 @Transactional(REQUIRED)을 걸면
  // 새 트랜잭션이 열리지 않은 채 조회가 돌아 위험하다(스프링 RestrictedTransactionalEventListenerFactory 가
  // REQUIRES_NEW/NOT_SUPPORTED 외엔 거부). 이 리스너는 읽기만 하고 푸시(APNs 네트워크 I/O)를 보낸다 —
  // NOT_SUPPORTED 로 트랜잭션을 잡지 않으면 각 조회가 짧게 커넥션을 쥐었다 즉시 반납하고, 느린 푸시 전송
  // 동안에는 커넥션을 0개 쥔다(2026-06 풀 고갈 인시던트 교훈: 긴 I/O 구간에 커넥션을 들고 있지 않는다).
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
      Long userId = link.getUserId();
      String label = label(link);
      long human = totals.countHumanByLinkId(linkId);

      if (human == 1L) {
        dispatcher.dispatch(userId, LinkNotificationType.FIRST_CLICK, label, "첫 클릭이 들어왔어요 🎉");
        return; // 첫 클릭이면 마일스톤/급증은 무의미
      }
      if (MILESTONES.contains(human)) {
        dispatcher.dispatch(userId, LinkNotificationType.MILESTONE, label, human + " 클릭을 넘었어요");
      }
      maybeVelocity(userId, linkId, label, human, event.occurredAt());
    } catch (Exception e) {
      log.warn("link click notification skipped: {}", e.toString());
    }
  }

  private void maybeVelocity(Long userId, Long linkId, String label, long human, Instant now) {
    if (human < props.velocityMinClicks()) {
      return;
    }
    Instant lastHour = now.minus(Duration.ofHours(1));
    long current = ranges.countHumanByLinkIdAndRange(linkId, lastHour, now);
    if (current < 3) {
      return;
    }
    long prior =
        ranges.countHumanByLinkIdAndRange(linkId, now.minus(Duration.ofHours(25)), lastHour);
    double baselinePerHour = Math.max(prior / 24.0, 0.5);
    double ratio = current / baselinePerHour;
    if (ratio < props.velocityRatio()) {
      return;
    }
    // 12시간 쿨다운 — 락을 잡고 풀지 않아 TTL 로 만료(같은 링크 급증 도배 방지).
    if (!cooldown.tryAcquire(VELOCITY_COOLDOWN + linkId, Duration.ofHours(12))) {
      return;
    }
    dispatcher.dispatch(
        userId,
        LinkNotificationType.VELOCITY_SPIKE,
        label,
        "지금 평소의 " + Math.round(ratio) + "배 — 어디서 퍼지는지 보세요");
  }

  private String label(LinkEntity link) {
    String note = link.getNote();
    if (note != null && !note.isBlank()) {
      return note;
    }
    return "/" + link.getShortCode();
  }
}
