package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageVariantSweepTest {

  private final ImageVariantSource notes = mock(ImageVariantSource.class);
  private final ImageVariantSource posts = mock(ImageVariantSource.class);
  private final ImageVariantLedger ledger = mock(ImageVariantLedger.class);
  private final ImageVariantQueue queue = mock(ImageVariantQueue.class);
  private final List<String> queued = new ArrayList<>();
  private final Set<String> done = Set.of("note-images/7/done.jpg");

  @BeforeEach
  void aLedgerThatKnowsOneOriginal() {
    when(ledger.unrecorded(anyCollection()))
        .thenAnswer(
            call ->
                ((Collection<String>) call.getArgument(0))
                    .stream().distinct().filter(key -> !done.contains(key)).toList());
    when(queue.enabled()).thenReturn(true);
    when(queue.idle()).thenReturn(true);
    when(queue.enqueue(anyString()))
        .thenAnswer(
            call -> {
              queued.add(call.getArgument(0));
              return true;
            });
  }

  private ImageVariantSweep sweep(boolean backfill) {
    return new ImageVariantSweep(
        List.of(notes, posts), ledger, queue, new ImageVariantProperties(true, 100, 50, backfill));
  }

  @Test
  void theRecentSweepQueuesNewestOriginalsThatHaveNoVariantsYet() {
    when(notes.newest(50))
        .thenReturn(
            List.of("note-images/7/new.jpg", "note-images/7/done.jpg", "note-images/7/a.gif"));
    when(posts.newest(50)).thenReturn(List.of("post-images/7/42/cover.png", ""));

    assertThat(sweep(false).sweepRecent()).isEqualTo(2);

    assertThat(queued).containsExactly("note-images/7/new.jpg", "post-images/7/42/cover.png");
  }

  @Test
  void nothingIsSweptWhileGenerationIsOff() {
    when(queue.enabled()).thenReturn(false);

    assertThat(sweep(true).sweepRecent()).isZero();
    assertThat(sweep(true).backfillStep()).isFalse();
    verify(notes, never()).newest(anyInt());
    verify(notes, never()).after(anyLong(), anyInt());
  }

  @Test
  void theBackfillWalksEverySourceOneOriginalPerStepAndThenStops() {
    when(notes.after(0, ImageVariantSweep.PAGE))
        .thenReturn(
            new ImageVariantSource.Page(
                12,
                List.of("note-images/7/a.jpg", "note-images/7/done.jpg", "note-images/7/b.png")));
    when(notes.after(12, ImageVariantSweep.PAGE))
        .thenReturn(new ImageVariantSource.Page(12, List.of()));
    when(posts.after(0, ImageVariantSweep.PAGE))
        .thenReturn(new ImageVariantSource.Page(3, List.of("post-images/7/42/c.jpg")));
    when(posts.after(3, ImageVariantSweep.PAGE))
        .thenReturn(new ImageVariantSource.Page(3, List.of()));
    ImageVariantSweep sweep = sweep(true);

    List<Boolean> steps = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      steps.add(sweep.backfillStep());
    }

    assertThat(queued)
        .containsExactly("note-images/7/a.jpg", "note-images/7/b.png", "post-images/7/42/c.jpg");
    assertThat(steps.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
    assertThat(steps.subList(0, 2)).containsOnly(true);
    assertThat(sweep.backfillStep()).isFalse();
    verify(notes).after(12, ImageVariantSweep.PAGE);
    verify(posts).after(3, ImageVariantSweep.PAGE);
  }

  @Test
  void theBackfillWaitsForTheWorkerAndRetriesWhatTheQueueRefused() {
    when(notes.after(0, ImageVariantSweep.PAGE))
        .thenReturn(new ImageVariantSource.Page(1, List.of("note-images/7/a.jpg")));
    ImageVariantSweep sweep = sweep(true);

    when(queue.idle()).thenReturn(false);
    assertThat(sweep.backfillStep()).isFalse();
    verify(notes, never()).after(anyLong(), anyInt());

    when(queue.idle()).thenReturn(true);
    when(queue.enqueue("note-images/7/a.jpg")).thenReturn(false).thenReturn(true);
    assertThat(sweep.backfillStep()).isFalse();
    assertThat(sweep.backfillStep()).isTrue();
    verify(notes).after(0, ImageVariantSweep.PAGE);
  }

  @Test
  void theBackfillStaysOffUnlessTurnedOn() {
    assertThat(sweep(false).backfillStep()).isFalse();
    verify(notes, never()).after(anyLong(), anyInt());
  }
}
