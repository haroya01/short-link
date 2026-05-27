package com.example.short_link.common.observability;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.ErrorHandler;

/**
 * Replaces Spring's default {@code TaskUtils$LoggingErrorHandler} which logs a generic "Unexpected
 * error occurred in scheduled task" line with no hint of *which* task threw. We walk the failing
 * exception's stack trace for the first frame inside our package, push it into MDC under the {@code
 * task} key (so {@link com.example.short_link.admin.application.RecentErrorsBuffer} can attach it
 * to the captured record), then re-log with the resolved name. Net effect: admin "recent errors"
 * pane shows {@code task=LinkWebhookDispatcher.flushBatches} instead of a useless generic message.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(
    prefix = "short-link.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledTaskErrorHandler implements SchedulingConfigurer {

  private static final String APP_PACKAGE = "com.example.short_link";

  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.setTaskScheduler(taskScheduler());
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("scheduled-");
    scheduler.setErrorHandler(new ContextAwareErrorHandler());
    // 5s shutdown grace — long enough for an in-flight webhook flush to finish, short enough
    // that test contexts don't drag for 30s on tearDown when a fixedDelay=5s job is mid-tick.
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(5);
    scheduler.initialize();
    return scheduler;
  }

  static class ContextAwareErrorHandler implements ErrorHandler {
    @Override
    public void handleError(Throwable t) {
      String taskName = resolveTaskName(t);
      if (taskName != null) MDC.put("task", taskName);
      try {
        log.error(
            "scheduled task failed: task={} thread={}",
            taskName == null ? "unknown" : taskName,
            Thread.currentThread().getName(),
            t);
      } finally {
        if (taskName != null) MDC.remove("task");
      }
    }

    private static String resolveTaskName(Throwable t) {
      for (StackTraceElement frame : t.getStackTrace()) {
        String cls = frame.getClassName();
        if (cls.startsWith(APP_PACKAGE)
            && !cls.contains("$$")
            && !cls.contains("CGLIB")
            && !cls.endsWith("$Proxy")) {
          String simple = cls.substring(cls.lastIndexOf('.') + 1);
          return simple + "." + frame.getMethodName();
        }
      }
      return null;
    }
  }
}
