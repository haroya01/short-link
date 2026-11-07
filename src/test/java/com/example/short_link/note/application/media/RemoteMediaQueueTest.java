package com.example.short_link.note.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.image.ImageVariantProperties;
import com.example.short_link.common.storage.ObjectStorage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteMediaQueueTest {

  private static final RemoteImage A = new RemoteImage(10L, "https://m.example/a.png", "image/png");
  private static final RemoteImage B =
      new RemoteImage(20L, "https://m.example/b.jpg", "image/jpeg");
  private static final RemoteImage C =
      new RemoteImage(30L, "https://m.example/c.jpg", "image/jpeg");

  private final RemoteMediaCopier copier = mock(RemoteMediaCopier.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final CountDownLatch release = new CountDownLatch(1);
  private final CountDownLatch started = new CountDownLatch(1);
  private RemoteMediaQueue queue;

  @BeforeEach
  void configuredStorage() {
    when(storage.isConfigured()).thenReturn(true);
  }

  @AfterEach
  void stop() {
    release.countDown();
    if (queue != null) {
      queue.stop();
    }
  }

  private RemoteMediaQueue queue(boolean enabled, int capacity, boolean variants) {
    return new RemoteMediaQueue(
        copier,
        new RemoteMediaProperties(enabled, capacity, 0, Duration.ofDays(30), 0),
        new ImageVariantProperties(variants, 0, 0, false),
        storage);
  }

  private void blockTheFirstCopy() {
    AtomicInteger calls = new AtomicInteger();
    doAnswer(
            call -> {
              if (calls.getAndIncrement() == 0) {
                started.countDown();
                release.await(10, TimeUnit.SECONDS);
              }
              return null;
            })
        .when(copier)
        .copy(anyString(), any(), anyList());
  }

  @Test
  void oneLowPriorityThreadCopiesOneImageAtATime() throws Exception {
    AtomicInteger running = new AtomicInteger();
    AtomicInteger most = new AtomicInteger();
    Thread[] worker = new Thread[1];
    doAnswer(
            call -> {
              worker[0] = Thread.currentThread();
              most.accumulateAndGet(running.incrementAndGet(), Math::max);
              Thread.sleep(50);
              running.decrementAndGet();
              return null;
            })
        .when(copier)
        .copy(anyString(), any(), anyList());
    queue = queue(true, 10, true);

    assertThat(queue.enqueue(A)).isTrue();
    assertThat(queue.enqueue(B)).isTrue();
    assertThat(queue.enqueue(C)).isTrue();

    verify(copier, timeout(5000).times(3)).copy(anyString(), any(), anyList());
    assertThat(most).hasValue(1);
    assertThat(worker[0].getName()).isEqualTo("kurl-remote-media");
    assertThat(worker[0].getPriority()).isEqualTo(Thread.MIN_PRIORITY);
    assertThat(worker[0].isDaemon()).isTrue();
  }

  @Test
  void enqueueReturnsAtOnceAndAFullQueueDropsTheImage() throws Exception {
    blockTheFirstCopy();
    queue = queue(true, 1, true);

    assertThat(queue.enqueue(A)).isTrue();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    long before = System.nanoTime();
    assertThat(queue.enqueue(B)).isTrue();
    assertThat(queue.enqueue(C)).isFalse();
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(50);

    release.countDown();
    verify(copier, timeout(5000)).copy(RemoteMediaKeys.keyOf(B.url(), B.type()), B, List.of(20L));
    verify(copier, never()).copy(RemoteMediaKeys.keyOf(C.url(), C.type()), C, List.of(30L));
  }

  @Test
  void theSameAddressWaitingIsFetchedOnceForEveryRowThatShowsIt() throws Exception {
    blockTheFirstCopy();
    queue = queue(true, 10, true);
    RemoteImage sameAsB = new RemoteImage(21L, B.url(), B.type());
    String keyB = RemoteMediaKeys.keyOf(B.url(), B.type());

    assertThat(queue.enqueue(A)).isTrue();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(queue.enqueue(B)).isTrue();
    assertThat(queue.enqueue(sameAsB)).isTrue();
    release.countDown();

    verify(copier, timeout(5000)).copy(keyB, B, List.of(20L, 21L));
    Thread.sleep(100);
    verify(copier, times(1)).copy(org.mockito.ArgumentMatchers.eq(keyB), any(), anyList());
  }

  @Test
  void anAddressArrivingAfterItsCopyStartedIsQueuedAgainSoItsRowIsNotLost() throws Exception {
    blockTheFirstCopy();
    queue = queue(true, 10, true);
    RemoteImage sameAsA = new RemoteImage(11L, A.url(), A.type());
    String keyA = RemoteMediaKeys.keyOf(A.url(), A.type());

    assertThat(queue.enqueue(A)).isTrue();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(queue.enqueue(sameAsA)).isTrue();
    release.countDown();

    verify(copier, timeout(5000)).copy(keyA, A, List.of(10L));
    verify(copier, timeout(5000)).copy(keyA, sameAsA, List.of(11L));
  }

  @Test
  void nothingIsCopiedWhenOffOrWithoutResizingButTheSweepStillClearsCopies() {
    queue = queue(false, 10, true);
    assertThat(queue.copying()).isFalse();
    assertThat(queue.enqueue(A)).isFalse();
    assertThat(queue.sweep()).isTrue();
    verify(copier, timeout(5000)).sweep();

    RemoteMediaQueue noVariants = queue(true, 10, false);
    assertThat(noVariants.enqueue(A)).isFalse();
    noVariants.stop();

    when(storage.isConfigured()).thenReturn(false);
    RemoteMediaQueue noBucket = queue(true, 10, true);
    assertThat(noBucket.enqueue(A)).isFalse();
    assertThat(noBucket.sweep()).isFalse();
    noBucket.stop();

    verify(copier, never()).copy(anyString(), any(), anyList());
  }

  @Test
  void aSweepIsNotQueuedTwice() throws Exception {
    CountDownLatch sweeping = new CountDownLatch(1);
    doAnswer(
            call -> {
              sweeping.countDown();
              release.await(10, TimeUnit.SECONDS);
              return null;
            })
        .when(copier)
        .sweep();
    queue = queue(true, 10, true);

    assertThat(queue.sweep()).isTrue();
    assertThat(sweeping.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(queue.sweep()).isFalse();
    release.countDown();

    verify(copier, timeout(5000)).sweep();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    boolean again = false;
    while (!again && System.nanoTime() < deadline) {
      again = queue.sweep();
      Thread.sleep(10);
    }
    assertThat(again).isTrue();
  }

  @Test
  void aQueueThatIsOnSaysItCopiesAndRefusesASweepOnceItsWorkerStopped() {
    queue = queue(true, 10, true);
    assertThat(queue.copying()).isTrue();

    queue.stop();

    assertThat(queue.sweep()).isFalse();
    verify(copier, never()).sweep();
  }
}
