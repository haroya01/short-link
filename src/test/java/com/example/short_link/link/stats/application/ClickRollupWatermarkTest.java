package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ClickRollupWatermarkTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final Instant TARGET = Instant.parse("2026-08-17T00:00:00Z");
  private static final Instant CLOSED = Instant.parse("2026-11-15T00:00:00Z");

  @Mock private ClickRollupRepository rollups;
  @Mock private PlatformTransactionManager transactions;
  @Captor private ArgumentCaptor<TransactionDefinition> opened;

  private ClickRollupService service;

  @BeforeEach
  void aServiceKeepingNinetyRawDays() {
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service =
        new ClickRollupService(rollups, new ClickRetentionProperties(90, true, 2), transactions);
  }

  @Test
  void rollingUpWithNoClicksLeftStillMovesTheWatermarkToTheBoundary() {
    Instant rolled = TARGET.minus(Duration.ofDays(3));
    when(rollups.rolledUntil()).thenReturn(rolled);
    when(rollups.firstClickAtOrAfter(rolled)).thenReturn(Optional.empty());

    int days = service.rollUp(NOW);

    assertThat(days).isZero();
    verify(rollups).advanceTo(TARGET);
    verify(rollups, never()).rollUpDay(any(), any());
  }

  @Test
  void aClickAtOrAfterTheBoundaryIsLeftRawAndTheWatermarkStopsAtTheBoundary() {
    Instant rolled = TARGET.minus(Duration.ofDays(3));
    when(rollups.rolledUntil()).thenReturn(rolled);
    when(rollups.firstClickAtOrAfter(rolled))
        .thenReturn(Optional.of(TARGET.plus(Duration.ofHours(1))));

    int days = service.rollUp(NOW);

    assertThat(days).isZero();
    verify(rollups).advanceTo(TARGET);
    verify(rollups, never()).rollUpDay(any(), any());
  }

  @Test
  void aRolledUpDayKeepsOneVisitorSketchPerLinkOfThatDaysPeople() {
    Instant day = TARGET.minus(Duration.ofDays(1));
    when(rollups.rolledUntil()).thenReturn(day);
    when(rollups.firstClickAtOrAfter(day)).thenReturn(Optional.of(day.plus(Duration.ofHours(2))));
    when(rollups.humanVisitors(day, TARGET))
        .thenReturn(
            List.of(
                new ClickRollupRepository.LinkVisitor(10L, "visitor-a"),
                new ClickRollupRepository.LinkVisitor(11L, "visitor-c"),
                new ClickRollupRepository.LinkVisitor(10L, "visitor-b")));

    service.rollUp(NOW);

    verify(rollups)
        .saveVisitorSketch(
            eq(10L), eq(day), aryEq(VisitorSketch.of(List.of("visitor-a", "visitor-b"))));
    verify(rollups)
        .saveVisitorSketch(eq(11L), eq(day), aryEq(VisitorSketch.of(List.of("visitor-c"))));
  }

  @Test
  void eachRolledUpDayMovesTheWatermarkToItsOwnEndInItsOwnCommittedTransaction() {
    Instant rolled = TARGET.minus(Duration.ofDays(2));
    Instant firstDay = rolled;
    Instant secondDay = rolled.plus(Duration.ofDays(1));
    when(rollups.rolledUntil()).thenReturn(rolled);
    when(rollups.firstClickAtOrAfter(firstDay))
        .thenReturn(Optional.of(firstDay.plus(Duration.ofHours(5))));
    when(rollups.firstClickAtOrAfter(secondDay))
        .thenReturn(Optional.of(secondDay.plus(Duration.ofHours(7))));
    when(rollups.humanVisitors(any(), any())).thenReturn(List.of());

    int days = service.rollUp(NOW);

    assertThat(days).isEqualTo(2);
    InOrder steps = inOrder(rollups, transactions);
    steps.verify(transactions).getTransaction(any());
    steps.verify(rollups).lockState();
    steps.verify(rollups).rollUpDay(firstDay, secondDay);
    steps.verify(rollups).advanceTo(secondDay);
    steps.verify(transactions).commit(any());
    steps.verify(transactions).getTransaction(any());
    steps.verify(rollups).lockState();
    steps.verify(rollups).rollUpDay(secondDay, TARGET);
    steps.verify(rollups).advanceTo(TARGET);
    steps.verify(transactions).commit(any());
  }

  @Test
  void aggregatingMovesTheWatermarkAfterEachDayAndThenToTheClosedBoundary() {
    Instant day = CLOSED.minus(Duration.ofDays(3));
    when(rollups.aggregatedUntil()).thenReturn(day.plus(Duration.ofDays(2)));
    when(rollups.rolledUntil()).thenReturn(TARGET);
    when(rollups.firstClickAtOrAfter(day)).thenReturn(Optional.of(day.plus(Duration.ofHours(1))));
    when(rollups.firstClickAtOrAfter(day.plus(Duration.ofDays(1)))).thenReturn(Optional.empty());

    int days = service.aggregate(NOW);

    assertThat(days).isEqualTo(1);
    InOrder steps = inOrder(rollups, transactions);
    steps.verify(rollups).aggregateDay(day, day.plus(Duration.ofDays(1)));
    steps.verify(rollups).advanceAggregatedTo(day.plus(Duration.ofDays(1)));
    steps.verify(transactions).commit(any());
    steps.verify(rollups).advanceAggregatedTo(CLOSED);
    steps.verify(transactions).commit(any());
  }

  @Test
  void aDayRunsInANewReadCommittedTransaction() {
    Instant rolled = TARGET.minus(Duration.ofDays(1));
    when(rollups.rolledUntil()).thenReturn(rolled);
    when(rollups.firstClickAtOrAfter(rolled)).thenReturn(Optional.empty());

    service.rollUp(NOW);

    verify(transactions).getTransaction(opened.capture());
    assertThat(opened.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(opened.getValue().getIsolationLevel())
        .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Test
  void theDeleteCommitsEachBatchInANewTransactionUntilABatchComesBackShort() {
    when(rollups.rolledUntil()).thenReturn(TARGET);
    when(rollups.deleteRawBefore(TARGET, 2)).thenReturn(2, 2, 1);

    long deleted = service.deleteRolledUp();

    assertThat(deleted).isEqualTo(5);
    verify(rollups, times(3)).deleteRawBefore(any(), anyInt());
    verify(transactions, times(3)).getTransaction(opened.capture());
    verify(transactions, times(3)).commit(any());
    assertThat(opened.getAllValues())
        .allSatisfy(
            definition ->
                assertThat(definition.getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
  }
}
