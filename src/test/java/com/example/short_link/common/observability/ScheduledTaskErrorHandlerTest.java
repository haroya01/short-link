package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ScheduledTaskErrorHandlerTest {

  @Test
  void taskSchedulerInitialised() {
    ScheduledTaskErrorHandler cfg = new ScheduledTaskErrorHandler();
    ThreadPoolTaskScheduler scheduler = cfg.taskScheduler();
    assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduled-");
    scheduler.shutdown();
  }

  @Test
  void errorHandlerNoOpsOnNonAppFrames() {
    ScheduledTaskErrorHandler.ContextAwareErrorHandler h =
        new ScheduledTaskErrorHandler.ContextAwareErrorHandler();
    RuntimeException ex = new RuntimeException("boom");
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1)
        });
    h.handleError(ex);
  }

  @Test
  void errorHandlerResolvesAppFrameAndLogs() {
    ScheduledTaskErrorHandler.ContextAwareErrorHandler h =
        new ScheduledTaskErrorHandler.ContextAwareErrorHandler();
    RuntimeException ex = new RuntimeException("boom");
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1),
          new StackTraceElement("com.example.short_link.SomeJob", "doIt", "SomeJob.java", 10)
        });
    h.handleError(ex);
  }

  @Test
  void errorHandlerSkipsCglibAndProxyFrames() {
    ScheduledTaskErrorHandler.ContextAwareErrorHandler h =
        new ScheduledTaskErrorHandler.ContextAwareErrorHandler();
    RuntimeException ex = new RuntimeException("boom");
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement(
              "com.example.short_link.SomeJob$$SpringCGLIB$$0", "doIt", "SomeJob.java", 10),
          new StackTraceElement("com.example.short_link.SomeJob$Proxy", "doIt", "SomeJob.java", 10),
          new StackTraceElement("com.example.short_link.RealJob", "tick", "RealJob.java", 5)
        });
    h.handleError(ex);
  }
}
