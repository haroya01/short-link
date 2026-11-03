package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.link.LinkOgFetchReopener;
import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository.Candidate;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository.Refill;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class NoteLinkPreviewRefillTest {

  private static final String NAME = "cross-host-redirects";
  private static final Instant T0 = Instant.parse("2026-11-03T00:00:00.123456789Z");
  private static final Refill PENDING = new Refill(NAME, false, Long.MAX_VALUE, 0, 0, 0, 0);
  private static final Refill RUNNING = new Refill(NAME, true, Long.MAX_VALUE, 2, 1, 0, 0);

  private final NoteLinkPreviewRefillRepository refills =
      mock(NoteLinkPreviewRefillRepository.class);
  private final NoteLinkPreviewService previews = mock(NoteLinkPreviewService.class);
  private final LinkPreviewReader reader = mock(LinkPreviewReader.class);
  private final LinkOgFetchReopener ogFetches = mock(LinkOgFetchReopener.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private final AtomicReference<Instant> now = new AtomicReference<>(T0);
  private final Clock clock = mock(Clock.class);
  private final NoteLinkPreviewRefill refill =
      new NoteLinkPreviewRefill(
          refills, previews, reader, ogFetches, new TransactionTemplate(transactions), clock);

  NoteLinkPreviewRefillTest() {
    when(clock.instant()).thenAnswer(invocation -> now.get());
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  }

  @Test
  void startingReopensGivenUpShortLinksAndClaimsTheRowInOneTransaction() {
    when(refills.pending()).thenReturn(Optional.of(PENDING));
    when(refills.countCandidates()).thenReturn(2L);
    when(ogFetches.reopenGivenUp()).thenReturn(1);
    when(refills.start(NAME, 2, 1, Instant.parse("2026-11-03T00:00:00.123456Z"))).thenReturn(true);

    refill.step();
    refill.step();

    var order = inOrder(transactions, refills, ogFetches);
    order.verify(transactions).getTransaction(any());
    order.verify(refills).countCandidates();
    order.verify(ogFetches).reopenGivenUp();
    order.verify(refills).start(NAME, 2, 1, Instant.parse("2026-11-03T00:00:00.123456Z"));
    ArgumentCaptor<TransactionStatus> committed = ArgumentCaptor.forClass(TransactionStatus.class);
    order.verify(transactions).commit(committed.capture());
    assertThat(committed.getValue().isRollbackOnly()).isFalse();
    verifyNoInteractions(previews, reader);
  }

  @Test
  void whenAnotherProcessClaimedTheRowFirstTheReopenRollsBackAndTheRowIsReadAgain() {
    when(refills.pending()).thenReturn(Optional.of(PENDING), Optional.of(RUNNING));
    when(refills.start(anyString(), anyLong(), anyInt(), any())).thenReturn(false);

    refill.step();
    refill.step();
    refill.step();

    ArgumentCaptor<TransactionStatus> committed = ArgumentCaptor.forClass(TransactionStatus.class);
    verify(transactions).commit(committed.capture());
    assertThat(committed.getValue().isRollbackOnly()).isTrue();
    verify(refills, times(2)).pending();
    verify(ogFetches).reopenGivenUp();
  }

  @Test
  void walksNotesFromTheNewestDownForgettingTheCachedCardBeforeEachRefresh() {
    when(refills.pending()).thenReturn(Optional.of(RUNNING));
    when(refills.newestBelow(Long.MAX_VALUE))
        .thenReturn(Optional.of(new Candidate(30, "이거 봐 https://youtu.be/x?si=1.")));
    when(refills.newestBelow(30))
        .thenReturn(Optional.of(new Candidate(20, "https://older.example/post")));
    when(previews.refresh(30L, "https://youtu.be/x?si=1")).thenReturn(true);
    when(previews.refresh(20L, "https://older.example/post")).thenReturn(false);

    refill.step();
    refill.step();
    now.set(T0.plusSeconds(1));
    refill.step();

    var order = inOrder(reader, previews, refills);
    order.verify(reader).forget("https://youtu.be/x?si=1");
    order.verify(previews).refresh(30L, "https://youtu.be/x?si=1");
    order.verify(refills).advance(new Refill(NAME, true, 30, 2, 1, 1, 0));
    order.verify(reader).forget("https://older.example/post");
    order.verify(previews).refresh(20L, "https://older.example/post");
    order.verify(refills).advance(new Refill(NAME, true, 20, 2, 1, 1, 1));
    verifyNoInteractions(ogFetches);
  }

  @Test
  void neverStartsASecondFetchWithinASecondOfTheLast() {
    when(refills.pending()).thenReturn(Optional.of(RUNNING));
    when(refills.newestBelow(anyLong()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new Candidate(
                        invocation.<Long>getArgument(0) == Long.MAX_VALUE
                            ? 100
                            : invocation.<Long>getArgument(0) - 1,
                        "https://a.example/")));

    refill.step();
    refill.step();
    refill.step();
    now.set(T0.plus(Duration.ofMillis(999)));
    refill.step();

    verify(previews, times(1)).refresh(anyLong(), anyString());
    verify(refills, times(1)).newestBelow(anyLong());

    now.set(T0.plusSeconds(1));
    refill.step();

    verify(previews).refresh(99L, "https://a.example/");
    verify(previews, times(2)).refresh(anyLong(), anyString());
  }

  @Test
  void aNoteWithoutAWebAddressMovesTheCursorWithoutFetchingOrSpendingThePace() {
    when(refills.pending()).thenReturn(Optional.of(RUNNING));
    when(refills.newestBelow(Long.MAX_VALUE))
        .thenReturn(Optional.of(new Candidate(30, "ftp://files.example/a")));
    when(refills.newestBelow(30)).thenReturn(Optional.of(new Candidate(20, "https://a.example/")));

    refill.step();
    refill.step();
    refill.step();

    verify(refills).advance(new Refill(NAME, true, 30, 2, 1, 0, 0));
    verify(reader, never()).forget("ftp://files.example/a");
    verify(previews).refresh(eq(20L), eq("https://a.example/"));
  }

  @Test
  void aNoteThatKeepsFailingCountsAsMissedInsteadOfHoldingTheCursor() {
    when(refills.pending()).thenReturn(Optional.of(RUNNING));
    when(refills.newestBelow(Long.MAX_VALUE))
        .thenReturn(Optional.of(new Candidate(30, "https://a.example/")));
    when(previews.refresh(30L, "https://a.example/"))
        .thenThrow(new IllegalStateException("database went away"));

    refill.step();
    refill.step();

    verify(refills).advance(new Refill(NAME, true, 30, 2, 1, 0, 1));
  }

  @Test
  void finishesWhenNoNoteIsLeftAndThenNeverTouchesTheDatabaseAgain() {
    when(refills.pending())
        .thenReturn(Optional.of(new Refill(NAME, true, 20, 2, 1, 1, 1)), Optional.empty());
    when(refills.newestBelow(20)).thenReturn(Optional.empty());

    refill.step();
    refill.step();
    refill.step();
    for (int i = 0; i < 5; i++) {
      refill.step();
    }

    verify(refills).finish(NAME, Instant.parse("2026-11-03T00:00:00.123456Z"));
    verify(refills, times(2)).pending();
    verify(refills).newestBelow(20);
    verifyNoMoreInteractions(refills);
    verifyNoInteractions(previews, reader, ogFetches);
  }

  @Test
  void aRefillFinishedBeforeARestartNeverRunsAgain() {
    when(refills.pending()).thenReturn(Optional.empty());

    for (int i = 0; i < 5; i++) {
      refill.step();
    }

    verify(refills).pending();
    verifyNoMoreInteractions(refills);
    verifyNoInteractions(previews, reader, ogFetches, transactions);
  }

  @Test
  void aRestartInTheMiddleResumesBelowTheSavedCursorWithoutReopeningAgain() {
    when(refills.pending()).thenReturn(Optional.of(new Refill(NAME, true, 500, 900, 3, 40, 60)));
    when(refills.newestBelow(500))
        .thenReturn(Optional.of(new Candidate(499, "https://a.example/")));
    when(previews.refresh(499L, "https://a.example/")).thenReturn(true);

    refill.step();
    refill.step();

    verify(refills).advance(new Refill(NAME, true, 499, 900, 3, 41, 60));
    verifyNoInteractions(ogFetches, transactions);
  }
}
