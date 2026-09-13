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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 잘못된 이벤트는 비콘 호출을 실패시키지 않고 제외한다. 방문자 해시는 글 조회와 같은 공식으로 만들되 Sec-GPC 요청에는 생성하지 않는다. */
@Slf4j
@Service
public class RecordBehaviorEventsUseCase {

  /** 배치 상한을 넘은 이벤트는 제외한다. */
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

  public RecordBehaviorEventsUseCase(
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

  /**
   * @return 저장한 이벤트 수
   */
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

  /** 분류 실패로 이벤트가 유실되지 않도록 미분류로 저장한다. */
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
