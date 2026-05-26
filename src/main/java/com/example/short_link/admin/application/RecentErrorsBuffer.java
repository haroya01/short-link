package com.example.short_link.admin.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.example.short_link.admin.application.dto.RecentError;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Logback appender shim that mirrors ERROR/WARN events into an in-memory ring buffer for the admin
 * UI. Captures exception class + cause chain, truncated stack trace, MDC fields populated by {@code
 * MdcFilter} (requestId/userId/method/uri/clientIp), and the {@code task} key set by the
 * scheduled-task wrapper so a recurring failure points back to the specific job that produced it.
 */
@Component
public class RecentErrorsBuffer {

  private static final int MAX_MESSAGE_LEN = 4_000;
  private static final int MAX_STACK_LEN = 8_000;
  private static final int MAX_CAUSE_DEPTH = 6;

  private final Deque<RecentError> buffer = new ConcurrentLinkedDeque<>();
  private final int capacity;
  private CapturingAppender appender;
  private LoggerContext context;

  public RecentErrorsBuffer(@Value("${short-link.admin.recent-errors-capacity:200}") int capacity) {
    this.capacity = Math.max(10, capacity);
  }

  @PostConstruct
  void install() {
    this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
    this.appender = new CapturingAppender(this);
    appender.setContext(context);
    appender.setName("recent-errors");
    appender.start();
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(appender);
  }

  @PreDestroy
  void uninstall() {
    if (appender != null && context != null) {
      Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
      root.detachAppender(appender);
      appender.stop();
    }
  }

  public List<RecentError> snapshot(int limit) {
    int size = Math.min(Math.max(1, limit), capacity);
    List<RecentError> list = new ArrayList<>(buffer);
    Collections.reverse(list);
    return list.size() <= size ? list : list.subList(0, size);
  }

  void capture(ILoggingEvent event) {
    // WARN included so non-fatal anomalies (rate-limit spikes, webhook retries) are reviewable
    // alongside hard errors — the UI filter lets operators narrow to ERROR-only when triaging.
    if (event.getLevel().toInt() < Level.WARN.toInt()) return;

    IThrowableProxy throwable = event.getThrowableProxy();
    String exceptionClass = throwable == null ? null : throwable.getClassName();
    String exceptionMessage = throwable == null ? null : throwable.getMessage();
    List<String> causeChain = buildCauseChain(throwable);
    String stackTrace =
        throwable == null ? null : truncate(ThrowableProxyUtil.asString(throwable), MAX_STACK_LEN);

    String message = truncate(event.getFormattedMessage(), MAX_MESSAGE_LEN);

    Map<String, String> mdc = event.getMDCPropertyMap();
    String requestId = mdcGet(mdc, "requestId");
    String requestUri = mdcGet(mdc, "uri");
    String requestMethod = mdcGet(mdc, "method");
    String userId = mdcGet(mdc, "userId");
    String clientIp = mdcGet(mdc, "clientIp");
    String taskName = mdcGet(mdc, "task");

    RecentError record =
        new RecentError(
            Instant.ofEpochMilli(event.getTimeStamp()),
            event.getLevel().toString(),
            event.getLoggerName(),
            event.getThreadName(),
            message,
            exceptionClass,
            exceptionMessage,
            causeChain,
            stackTrace,
            requestId,
            requestUri,
            requestMethod,
            userId,
            clientIp,
            taskName);
    buffer.addLast(record);
    while (buffer.size() > capacity) {
      buffer.pollFirst();
    }
  }

  private static String mdcGet(Map<String, String> mdc, String key) {
    if (mdc == null) return null;
    String value = mdc.get(key);
    return value == null || value.isBlank() ? null : value;
  }

  private static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static List<String> buildCauseChain(IThrowableProxy throwable) {
    if (throwable == null) return List.of();
    List<String> chain = new ArrayList<>();
    IThrowableProxy current = throwable.getCause();
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_DEPTH) {
      String label =
          current.getMessage() == null
              ? current.getClassName()
              : current.getClassName() + ": " + current.getMessage();
      chain.add(truncate(label, 500));
      current = current.getCause();
      depth++;
    }
    return chain;
  }

  static class CapturingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final RecentErrorsBuffer target;

    CapturingAppender(RecentErrorsBuffer target) {
      this.target = target;
    }

    @Override
    protected void append(ILoggingEvent event) {
      target.capture(event);
    }
  }
}
