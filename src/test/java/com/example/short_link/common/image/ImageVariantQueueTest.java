package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageVariantQueueTest {

  private final ImageVariantGenerator generator = mock(ImageVariantGenerator.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final CountDownLatch release = new CountDownLatch(1);
  private final CountDownLatch started = new CountDownLatch(1);
  private ImageVariantQueue queue;

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

  private void blockTheWorker() {
    doAnswer(
            call -> {
              started.countDown();
              release.await(10, TimeUnit.SECONDS);
              return null;
            })
        .when(generator)
        .generate(anyString());
  }

  @Test
  void enqueueReturnsWhileTheWorkerIsStillGenerating() throws Exception {
    blockTheWorker();
    queue =
        new ImageVariantQueue(generator, new ImageVariantProperties(true, 2, 0, false), storage);

    assertThat(queue.enqueue("note-images/7/a.jpg")).isTrue();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    long before = System.nanoTime();
    assertThat(queue.enqueue("note-images/7/b.jpg")).isTrue();
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(50);
    assertThat(queue.idle()).isFalse();

    release.countDown();
    verify(generator, timeout(5000)).generate("note-images/7/b.jpg");
  }

  @Test
  void aFullQueueDropsTheJobInsteadOfWaiting() throws Exception {
    blockTheWorker();
    queue =
        new ImageVariantQueue(generator, new ImageVariantProperties(true, 1, 0, false), storage);

    assertThat(queue.enqueue("note-images/7/a.jpg")).isTrue();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(queue.enqueue("note-images/7/b.jpg")).isTrue();
    long before = System.nanoTime();
    assertThat(queue.enqueue("note-images/7/c.jpg")).isFalse();
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(50);

    release.countDown();
    verify(generator, timeout(5000)).generate("note-images/7/b.jpg");
    Thread.sleep(100);
    verify(generator, never()).generate("note-images/7/c.jpg");
    assertThat(queue.enqueue("note-images/7/c.jpg")).isTrue();
  }

  @Test
  void theSameOriginalIsNotQueuedTwiceWhileWaiting() throws Exception {
    blockTheWorker();
    queue =
        new ImageVariantQueue(generator, new ImageVariantProperties(true, 5, 0, false), storage);

    assertThat(queue.enqueue("note-images/7/a.jpg")).isTrue();
    assertThat(queue.enqueue("note-images/7/a.jpg")).isFalse();
  }

  @Test
  void ineligibleKeysAndADisabledQueueDoNothing() {
    queue =
        new ImageVariantQueue(generator, new ImageVariantProperties(true, 5, 0, false), storage);
    assertThat(queue.enqueue("note-images/7/a.gif")).isFalse();
    assertThat(queue.enqueue("avatars/7/a.jpg")).isFalse();

    ImageVariantQueue off =
        new ImageVariantQueue(generator, new ImageVariantProperties(false, 5, 0, false), storage);
    assertThat(off.enabled()).isFalse();
    assertThat(off.enqueue("note-images/7/a.jpg")).isFalse();
    off.stop();

    ObjectStorage unconfigured = mock(ObjectStorage.class);
    ImageVariantQueue noBucket =
        new ImageVariantQueue(
            generator, new ImageVariantProperties(true, 5, 0, false), unconfigured);
    assertThat(noBucket.enabled()).isFalse();
    assertThat(noBucket.enqueue("note-images/7/a.jpg")).isFalse();
    noBucket.stop();
    verify(generator, never()).generate(anyString());
  }

  @Test
  void theWorkerIsOneLowPriorityThread() throws Exception {
    Thread[] worker = new Thread[1];
    doAnswer(
            call -> {
              worker[0] = Thread.currentThread();
              started.countDown();
              return null;
            })
        .when(generator)
        .generate(anyString());
    queue =
        new ImageVariantQueue(generator, new ImageVariantProperties(true, 5, 0, false), storage);

    queue.enqueue("note-images/7/a.jpg");

    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(worker[0].getPriority()).isEqualTo(Thread.MIN_PRIORITY);
    assertThat(worker[0].isDaemon()).isTrue();
    assertThat(worker[0].getName()).isEqualTo("kurl-image-variants");
  }
}
