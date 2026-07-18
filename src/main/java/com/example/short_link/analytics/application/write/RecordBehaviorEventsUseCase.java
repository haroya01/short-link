package com.example.short_link.analytics.application.write;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 독자 행동 이벤트 배치 적재. 비콘 계약이라 실패가 사용자에게 새면 안 된다 — 화이트리스트에 안 맞는 건은 에러 대신 조용히 드랍하고, 수용/드랍 카운트만 미터로 남긴다.
 * 봇 판정(UA→버스트→데이터센터)과 방문자 해시는 {@code RecordPostViewUseCase} 의 조회 경로와 같은 공식을 재사용해, 도달과 행동이 같은 기준으로
 * 걸러지고 조인되게 한다. Sec-GPC 방문자는 해시를 만들지 않는다(세션 내 퍼널만, 재방문 추적 없음).
 */
@Slf4j
@Service
public class RecordBehaviorEventsUseCase {

  /** 한 배치에서 받아주는 최대 건수 — 그 밖은 드랍(비콘 플러시 주기상 정상 트래픽은 한 자릿수). */
  static final int MAX_BATCH = 25;

  private static final Set<String> EVENT_NAMES =
      Set.of("read_progress", "second_action", "cta_click");
  private static final Set<String> TARGET_TYPES =
      Set.of("post", "connection", "profile", "series", "tag");
  private static final Set<Integer> DEPTH_MILESTONES = Set.of(25, 50, 75, 100);
  private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{8,40}");
  private static final long MAX_DWELL_MS = 6L * 60 * 60 * 1000;

  private final BehaviorEventRepository repository;
  private final UserAgentClassifier userAgentClassifier;
  private final AsnResolver asnResolver;
  private final BotHeuristic botHeuristic;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  @Autowired
  public RecordBehaviorEventsUseCase(
      BehaviorEventRepository repository,
      UserAgentClassifier userAgentClassifier,
      AsnResolver asnResolver,
      BotHeuristic botHeuristic,
      MeterRegistry meterRegistry) {
    this(
        repository,
        userAgentClassifier,
        asnResolver,
        botHeuristic,
        meterRegistry,
        Clock.systemUTC());
  }

  RecordBehaviorEventsUseCase(
      BehaviorEventRepository repository,
      UserAgentClassifier userAgentClassifier,
      AsnResolver asnResolver,
      BotHeuristic botHeuristic,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.repository = repository;
    this.userAgentClassifier = userAgentClassifier;
    this.asnResolver = asnResolver;
    this.botHeuristic = botHeuristic;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  /** 수용한 건수를 돌려준다(비콘 응답에는 안 실리고 테스트·미터 용). */
  @Transactional
  public int execute(String sessionId, List<BehaviorEventCommand> batch, BehaviorContext ctx) {
    if (batch == null || batch.isEmpty()) return 0;
    String session =
        sessionId != null && SESSION_ID.matcher(sessionId).matches() ? sessionId : null;

    Classification cls = classify(ctx);
    List<BehaviorEventEntity> rows = new ArrayList<>();
    for (BehaviorEventCommand cmd : batch) {
      if (rows.size() >= MAX_BATCH) break;
      BehaviorEventEntity row = toRow(cmd, session, cls, ctx);
      if (row != null) rows.add(row);
    }
    if (!rows.isEmpty()) repository.saveAll(rows);
    meterRegistry.counter("behavior.events.accepted").increment(rows.size());
    meterRegistry.counter("behavior.events.dropped").increment(batch.size() - rows.size());
    return rows.size();
  }

  private BehaviorEventEntity toRow(
      BehaviorEventCommand cmd, String session, Classification cls, BehaviorContext ctx) {
    if (cmd == null || cmd.name() == null || !EVENT_NAMES.contains(cmd.name())) return null;
    String targetType = cmd.targetType();
    String targetId = cmd.targetId();
    Integer depth = cmd.depthPct();
    Long dwell = cmd.dwellMs();
    switch (cmd.name()) {
      case "second_action" -> {
        if (targetType == null || !TARGET_TYPES.contains(targetType)) return null;
        if (targetId != null && targetId.length() > 64) return null;
        depth = null;
        dwell = null;
      }
      case "read_progress" -> {
        if (depth != null && !DEPTH_MILESTONES.contains(depth)) return null;
        if (dwell != null && (dwell < 0 || dwell > MAX_DWELL_MS)) return null;
        if (depth == null && dwell == null) return null;
        targetType = null;
        targetId = null;
      }
      case "cta_click" -> {
        if (targetId != null && targetId.length() > 64) return null;
        targetType = null;
        depth = null;
        dwell = null;
      }
      default -> {
        return null;
      }
    }
    return BehaviorEventEntity.builder()
        .eventName(cmd.name())
        .occurredAt(clock.instant())
        .sessionId(session)
        .postId(cmd.postId())
        .targetType(targetType)
        .targetId(targetId)
        .depthPct(depth)
        .dwellMs(dwell)
        .deviceClass(cls.deviceClass())
        .bot(cls.bot())
        .botName(cls.botName())
        .visitorHash(
            !ctx.gpc() && cmd.postId() != null
                ? VisitorHasher.hash(cmd.postId(), ctx.clientIp(), ctx.userAgent())
                : null)
        .build();
  }

  /** 배치는 한 요청 = 한 방문자라 판정은 요청당 한 번만 돌린다. 판정 실패는 행동을 잃을 이유가 아니다 — 미분류로 폴백. */
  private Classification classify(BehaviorContext ctx) {
    try {
      UserAgentInfo ua = userAgentClassifier.classify(ctx.userAgent());
      boolean bot = ua.bot();
      String botName = ua.botName();
      if (!bot && botHeuristic.isSuspectBurst(ctx.clientIp())) {
        bot = true;
        botName = BotHeuristic.SUSPECT_LABEL;
      } else if (!bot) {
        AsnResolver.AsnInfo asn = asnResolver.resolve(ctx.clientIp());
        if (asn.datacenter()) {
          bot = true;
          botName = "datacenter:" + (asn.organization() == null ? "unknown" : asn.organization());
        }
      }
      return new Classification(ua.deviceClass(), bot, botName);
    } catch (RuntimeException e) {
      log.warn("behavior event classification failed", e);
      return new Classification(null, false, null);
    }
  }

  private record Classification(String deviceClass, boolean bot, String botName) {}
}
