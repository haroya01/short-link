package com.example.short_link.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Wraps every {@code @Scheduled} method with a Micrometer {@link Timer} so the admin can see which
 * background jobs are slow / erroring without instrumenting each call site by hand. The task tag is
 * {@code SimpleClassName.methodName} so dashboards read the bare task identity (e.g. {@code
 * OgFetchRetryJob.run}, {@code LinkWebhookDispatcher.flushBatches}) — no package prefix, no FQN
 * noise.
 *
 * <p>Exceptions from the proceeding call are re-thrown unchanged after tagging the timer {@code
 * result=error}; we never swallow a failure for observability's sake. Spring's scheduled-task
 * infrastructure already handles "next firing" decisions, so it sees the same Throwable it would
 * have seen without this aspect.
 */
@Slf4j
@Aspect
@Component
public class ScheduledTaskMetricsAspect {

  private final MeterRegistry registry;

  public ScheduledTaskMetricsAspect(MeterRegistry registry) {
    this.registry = registry;
  }

  @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
  public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    String taskName =
        pjp.getSignature().getDeclaringType().getSimpleName() + "." + pjp.getSignature().getName();
    Timer.Sample sample = Timer.start(registry);
    String result = "ok";
    try {
      return pjp.proceed();
    } catch (Throwable t) {
      result = "error";
      log.warn("scheduled task failed: task={} reason={}", taskName, t.toString());
      throw t;
    } finally {
      sample.stop(registry.timer("scheduled.task", "task", taskName, "result", result));
    }
  }
}
