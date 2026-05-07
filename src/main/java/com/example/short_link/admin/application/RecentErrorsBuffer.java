package com.example.short_link.admin.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecentErrorsBuffer {

  private static final int MAX_MESSAGE_LEN = 4_000;

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
    if (event.getLevel().toInt() < Level.ERROR.toInt()) return;
    String exception = null;
    IThrowableProxy throwable = event.getThrowableProxy();
    if (throwable != null) {
      exception = ThrowableProxyUtil.asString(throwable);
      if (exception != null && exception.length() > MAX_MESSAGE_LEN) {
        exception = exception.substring(0, MAX_MESSAGE_LEN);
      }
    }
    String message = event.getFormattedMessage();
    if (message != null && message.length() > MAX_MESSAGE_LEN) {
      message = message.substring(0, MAX_MESSAGE_LEN);
    }
    String requestId =
        event.getMDCPropertyMap() == null ? null : event.getMDCPropertyMap().get("requestId");
    RecentError record =
        new RecentError(
            Instant.ofEpochMilli(event.getTimeStamp()),
            event.getLevel().toString(),
            event.getLoggerName(),
            message,
            requestId,
            exception);
    buffer.addLast(record);
    while (buffer.size() > capacity) {
      buffer.pollFirst();
    }
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
