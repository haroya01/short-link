package com.example.short_link.link.stats.application;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClickExclusionService {

  public static final String EXCLUDED_BOT_NAME = ClickRecorder.PREVIEW_BOT_NAME_PREFIX + "excluded";

  private final LinkRepository links;
  private final ClickEventRepository clicks;
  private final ClickRollupRepository rollups;
  private final AuditLogService auditLog;
  private final TransactionTemplate transaction;

  public ClickExclusionService(
      LinkRepository links,
      ClickEventRepository clicks,
      ClickRollupRepository rollups,
      AuditLogService auditLog,
      PlatformTransactionManager transactionManager) {
    this.links = links;
    this.clicks = clicks;
    this.rollups = rollups;
    this.auditLog = auditLog;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public static boolean isExcluded(ClickEventEntity click) {
    return click.isBot() && EXCLUDED_BOT_NAME.equals(click.getBotName());
  }

  public int exclude(Long adminUserId, ShortCode shortCode, List<Long> clickIds) {
    return change(
        adminUserId, shortCode, clickIds, AuditAction.LINK_CLICKS_EXCLUDED, clicks::markHumansAs);
  }

  public int restore(Long adminUserId, ShortCode shortCode, List<Long> clickIds) {
    return change(
        adminUserId, shortCode, clickIds, AuditAction.LINK_CLICKS_RESTORED, clicks::markAsHumans);
  }

  private int change(
      Long adminUserId,
      ShortCode shortCode,
      List<Long> clickIds,
      AuditAction action,
      Marking marking) {
    LinkEntity link =
        links
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    Long linkId = link.linkId().value();
    int changed =
        Objects.requireNonNull(
            transaction.execute(
                status -> {
                  ClickRollupRepository.State state = rollups.lockState();
                  int rows = marking.mark(linkId, clickIds, EXCLUDED_BOT_NAME, state.rolledUntil());
                  if (rows > 0) sumAgain(linkId, clickIds, state);
                  return rows;
                }));
    auditLog.record(
        action,
        "link",
        shortCode.value(),
        adminUserId,
        Map.of("requested", clickIds.size(), "changed", changed));
    return changed;
  }

  private void sumAgain(Long linkId, List<Long> clickIds, ClickRollupRepository.State state) {
    List<Instant> days =
        clicks.findDayStartsOf(linkId, clickIds, state.rolledUntil(), state.aggregatedUntil());
    for (Instant dayStart : days) {
      rollups.aggregateLinkDay(linkId, dayStart, dayStart.plus(Duration.ofDays(1)));
    }
  }

  private interface Marking {
    int mark(Long linkId, List<Long> clickIds, String botName, Instant rolledUntil);
  }
}
