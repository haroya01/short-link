package com.example.short_link.admin.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.example.short_link.admin.application.RecentErrorsBuffer;
import com.example.short_link.admin.application.dto.RecentError;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecentErrorsLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static final int MAX_MESSAGE_LEN = 4_000;
  private static final int MAX_STACK_LEN = 8_000;
  private static final int MAX_CAUSE_DEPTH = 6;

  private final RecentErrorsBuffer buffer;
  private LoggerContext loggerContext;

  public RecentErrorsLogAppender(RecentErrorsBuffer buffer) {
    this.buffer = buffer;
  }

  @PostConstruct
  public void install() {
    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    setContext(loggerContext);
    setName("recent-errors");
    start();
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
  }

  @PreDestroy
  public void uninstall() {
    if (loggerContext != null) {
      loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
      stop();
    }
  }

  @Override
  protected void append(ILoggingEvent event) {
    // 장애 전조도 조사할 수 있도록 WARN을 함께 보관한다.
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
    buffer.record(record);
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
}
