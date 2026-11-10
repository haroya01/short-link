package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

class ClickExclusionServiceTest {

  private static final ShortCode CODE = new ShortCode("excl001");
  private static final Long LINK_ID = 41L;
  private static final Long ADMIN = 7L;
  private static final Instant ROLLED = Instant.parse("2026-08-01T00:00:00Z");
  private static final Instant SUMMED = Instant.parse("2026-11-10T00:00:00Z");
  private static final List<Long> IDS = List.of(11L, 12L, 13L);

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickEventRepository clicks = mock(ClickEventRepository.class);
  private final ClickRollupRepository rollups = mock(ClickRollupRepository.class);
  private final AuditLogService auditLog = mock(AuditLogService.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private final ClickExclusionService service =
      new ClickExclusionService(links, clicks, rollups, auditLog, transactions);

  @BeforeEach
  void aLinkAndTheBoundaries() {
    LinkEntity link = mock(LinkEntity.class);
    when(link.linkId()).thenReturn(new LinkId(LINK_ID));
    when(links.findByShortCode(CODE)).thenReturn(Optional.of(link));
    when(rollups.lockState()).thenReturn(new ClickRollupRepository.State(ROLLED, SUMMED));
  }

  @Test
  void excludingSumsEachSummedDayOfTheChangedClicksAgainInsideTheLockedTransaction() {
    Instant first = Instant.parse("2026-11-03T00:00:00Z");
    Instant second = Instant.parse("2026-11-08T00:00:00Z");
    when(clicks.markHumansAs(LINK_ID, IDS, "preview:excluded", ROLLED)).thenReturn(2);
    when(clicks.findDayStartsOf(LINK_ID, IDS, ROLLED, SUMMED)).thenReturn(List.of(first, second));

    assertThat(service.exclude(ADMIN, CODE, IDS)).isEqualTo(2);

    InOrder order = inOrder(transactions, rollups, clicks, auditLog);
    order
        .verify(transactions)
        .getTransaction(
            argThat(
                definition ->
                    definition.getIsolationLevel()
                        == TransactionDefinition.ISOLATION_READ_COMMITTED));
    order.verify(rollups).lockState();
    order.verify(clicks).markHumansAs(LINK_ID, IDS, "preview:excluded", ROLLED);
    order.verify(rollups).aggregateLinkDay(LINK_ID, first, first.plus(Duration.ofDays(1)));
    order.verify(rollups).aggregateLinkDay(LINK_ID, second, second.plus(Duration.ofDays(1)));
    order.verify(transactions).commit(any());
    order
        .verify(auditLog)
        .record(
            AuditAction.LINK_CLICKS_EXCLUDED,
            "link",
            "excl001",
            ADMIN,
            Map.of("requested", 3, "changed", 2));
  }

  @Test
  void restoringPutsBackOnlyTheExclusionLabelAndLogsItAsARestore() {
    when(clicks.markAsHumans(LINK_ID, IDS, "preview:excluded", ROLLED)).thenReturn(3);
    when(clicks.findDayStartsOf(LINK_ID, IDS, ROLLED, SUMMED)).thenReturn(List.of());

    assertThat(service.restore(ADMIN, CODE, IDS)).isEqualTo(3);

    verify(clicks, never()).markHumansAs(any(), any(), any(), any());
    verify(rollups, never()).aggregateLinkDay(any(), any(), any());
    verify(auditLog)
        .record(
            AuditAction.LINK_CLICKS_RESTORED,
            "link",
            "excl001",
            ADMIN,
            Map.of("requested", 3, "changed", 3));
  }

  @Test
  void aRequestThatChangesNothingSumsNothingButIsStillLogged() {
    when(clicks.markHumansAs(LINK_ID, IDS, "preview:excluded", ROLLED)).thenReturn(0);

    assertThat(service.exclude(ADMIN, CODE, IDS)).isZero();

    verify(clicks, never()).findDayStartsOf(any(), any(), any(), any());
    verify(rollups, never()).aggregateLinkDay(any(), any(), any());
    verify(auditLog)
        .record(
            AuditAction.LINK_CLICKS_EXCLUDED,
            "link",
            "excl001",
            ADMIN,
            Map.of("requested", 3, "changed", 0));
  }

  @Test
  void anUnknownLinkStopsBeforeTheLock() {
    when(links.findByShortCode(CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.exclude(ADMIN, CODE, IDS)).isInstanceOf(LinkException.class);

    verifyNoInteractions(transactions, rollups, clicks, auditLog);
  }

  @Test
  void onlyABotClickCarryingTheExclusionLabelCountsAsExcluded() {
    assertThat(ClickExclusionService.isExcluded(click(true, "preview:excluded"))).isTrue();
    assertThat(ClickExclusionService.isExcluded(click(true, "preview:operator"))).isFalse();
    assertThat(ClickExclusionService.isExcluded(click(true, null))).isFalse();
    assertThat(ClickExclusionService.isExcluded(click(false, null))).isFalse();
    assertThat(ClickExclusionService.isExcluded(click(false, "preview:excluded"))).isFalse();
  }

  private static ClickEventEntity click(boolean bot, String botName) {
    return ClickEventEntity.builder().linkId(new LinkId(LINK_ID)).bot(bot).botName(botName).build();
  }
}
