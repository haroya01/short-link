package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HostGateTest {

  private final HostGate gate =
      new HostGate(new BlogImportProperties(null, 0, null, Duration.ofMillis(200)));

  @Test
  void oneRequestPerHostAtATimeWhileOtherHostsGoAhead() throws Exception {
    AtomicInteger inFlight = new AtomicInteger();
    AtomicInteger most = new AtomicInteger();
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<String> holder =
        CompletableFuture.supplyAsync(
            () ->
                gate.call(
                    "https://rss.blog.naver.com/a.xml",
                    () -> {
                      most.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                      await(release);
                      inFlight.decrementAndGet();
                      return "first";
                    }));
    while (inFlight.get() == 0) {
      Thread.sleep(5);
    }

    assertThat(gate.call("https://v2.velog.io/rss/@w", () -> "other host")).isEqualTo("other host");
    assertThatThrownBy(() -> gate.call("https://RSS.blog.naver.com/b.xml", () -> "second"))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e ->
                assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE));

    release.countDown();
    assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("first");
    assertThat(gate.call("https://rss.blog.naver.com/b.xml", () -> "after")).isEqualTo("after");
    assertThat(most.get()).isEqualTo(1);
  }

  @Test
  void theTurnIsReturnedEvenWhenTheRequestFails() {
    assertThatThrownBy(
            () ->
                gate.call(
                    "https://x.example/a",
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(gate.call("https://x.example/b", () -> "ok")).isEqualTo("ok");
    assertThat(HostGate.host("not a url at all")).isEqualTo("not a url at all");
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
