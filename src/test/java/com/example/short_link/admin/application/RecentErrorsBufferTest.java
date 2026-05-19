package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RecentErrorsBufferTest {

  private final Logger log = LoggerFactory.getLogger(RecentErrorsBufferTest.class);
  private RecentErrorsBuffer buffer;

  @AfterEach
  void teardown() {
    if (buffer != null) buffer.uninstall();
  }

  @Test
  void capturesErrorsInReverseChronologicalOrder() {
    buffer = new RecentErrorsBuffer(100);
    buffer.install();

    log.error("first error");
    log.error("second error");
    log.error("third error");

    var snap = buffer.snapshot(10);
    assertThat(snap).hasSizeGreaterThanOrEqualTo(3);
    assertThat(snap.get(0).message()).isEqualTo("third error");
    assertThat(snap.get(1).message()).isEqualTo("second error");
    assertThat(snap.get(2).message()).isEqualTo("first error");
  }

  @Test
  void ignoresBelowWarnLevel() {
    buffer = new RecentErrorsBuffer(100);
    buffer.install();

    log.info("info message");
    log.debug("debug message");
    log.warn("warn message");
    log.error("real error");

    var snap = buffer.snapshot(10);
    assertThat(snap)
        .extracting(RecentError::message)
        .contains("real error", "warn message")
        .doesNotContain("info message", "debug message");
  }

  @Test
  void evictsOldestWhenCapacityReached() {
    buffer = new RecentErrorsBuffer(10);
    buffer.install();

    for (int i = 0; i < 5; i++) log.error("err {}", i);

    var snap = buffer.snapshot(20);
    assertThat(snap).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void capturesExceptionStack() {
    buffer = new RecentErrorsBuffer(50);
    buffer.install();

    log.error("boom", new RuntimeException("bang"));

    var snap = buffer.snapshot(5);
    var first = snap.get(0);
    assertThat(first.message()).isEqualTo("boom");
    assertThat(first.exceptionClass()).isEqualTo("java.lang.RuntimeException");
    assertThat(first.exceptionMessage()).isEqualTo("bang");
    assertThat(first.stackTrace()).contains("bang").contains("RuntimeException");
    assertThat(first.thread()).isNotBlank();
  }

  @Test
  void capturesCauseChain() {
    buffer = new RecentErrorsBuffer(50);
    buffer.install();

    RuntimeException root = new RuntimeException("root cause");
    IllegalStateException middle = new IllegalStateException("middle wrap", root);
    log.error("outer fail", new RuntimeException("outer", middle));

    var snap = buffer.snapshot(5);
    var first = snap.get(0);
    assertThat(first.causeChain()).hasSize(2);
    assertThat(first.causeChain().get(0)).contains("IllegalStateException").contains("middle wrap");
    assertThat(first.causeChain().get(1)).contains("RuntimeException").contains("root cause");
  }

  @Test
  void capturesWarnLevelToo() {
    buffer = new RecentErrorsBuffer(50);
    buffer.install();

    log.warn("slow query");
    log.error("hard error");

    var snap = buffer.snapshot(10);
    assertThat(snap).extracting(RecentError::message).containsExactly("hard error", "slow query");
    assertThat(snap.get(1).level()).isEqualTo("WARN");
  }

  @Test
  void capturesThreadName() {
    buffer = new RecentErrorsBuffer(20);
    buffer.install();

    log.error("from main thread");

    var snap = buffer.snapshot(5);
    assertThat(snap.get(0).thread()).isEqualTo(Thread.currentThread().getName());
  }

  @Test
  void minimumCapacityIs10() {
    buffer = new RecentErrorsBuffer(1);
    buffer.install();

    for (int i = 0; i < 15; i++) log.error("e{}", i);

    var snap = buffer.snapshot(20);
    assertThat(snap).hasSize(10);
  }

  @Test
  void truncatesVeryLongMessage() {
    buffer = new RecentErrorsBuffer(20);
    buffer.install();

    String big = "X".repeat(8000);
    log.error(big);

    var snap = buffer.snapshot(5);
    assertThat(snap.get(0).message()).hasSizeLessThanOrEqualTo(4000);
  }

  @Test
  void limitClampsTwoCapacityWhenLargerRequested() {
    buffer = new RecentErrorsBuffer(20);
    buffer.install();

    for (int i = 0; i < 5; i++) log.error("e{}", i);

    var snap = buffer.snapshot(9999);
    assertThat(snap).hasSizeLessThanOrEqualTo(20);
  }
}
