package com.example.short_link.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Tags scheduled timers as {@code SimpleClassName.methodName}. Exceptions are tagged as errors and
 * rethrown unchanged so Spring retains control of subsequent firings.
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
