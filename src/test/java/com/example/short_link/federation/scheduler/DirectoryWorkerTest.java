package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.directory.DirectoryProperties;
import com.example.short_link.federation.application.directory.DirectorySync;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DirectoryWorkerTest {

  private static DirectoryProperties props(boolean enabled) {
    return new DirectoryProperties(enabled, 100, 1_000, null, null, null, null);
  }

  @Test
  void nothingRunsWhereDirectoriesOrSchedulingAreOff() {
    DirectorySync sync = mock(DirectorySync.class);

    assertThat(new DirectoryWorker(sync, props(false), true).trigger()).isFalse();
    assertThat(new DirectoryWorker(sync, props(true), false).trigger()).isFalse();
    verify(sync, never()).run();
  }

  @Test
  void aRunGoesToItsOwnThreadAndASecondAskWhileItGoesIsNotStarted() throws Exception {
    DirectorySync sync = mock(DirectorySync.class);
    CountDownLatch release = new CountDownLatch(1);
    String[] thread = new String[1];
    when(sync.run())
        .thenAnswer(
            call -> {
              thread[0] = Thread.currentThread().getName();
              release.await(5, TimeUnit.SECONDS);
              return List.of();
            });
    DirectoryWorker worker = new DirectoryWorker(sync, props(true), true);

    assertThat(worker.trigger()).isTrue();
    verify(sync, timeout(2_000)).run();
    assertThat(worker.running()).isTrue();
    assertThat(worker.trigger()).isFalse();
    release.countDown();
    for (int i = 0; i < 50 && worker.running(); i++) {
      Thread.sleep(20);
    }

    assertThat(worker.running()).isFalse();
    assertThat(thread[0]).isEqualTo("kurl-directory");
    worker.stop();
  }
}
