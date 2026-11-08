package com.example.short_link.common.cache.web;

import com.example.short_link.common.cache.WebCacheRevalidator;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class WebRevalidateClient implements WebCacheRevalidator, DisposableBean {

  private static final Duration WINDOW = Duration.ofSeconds(2);
  private static final String SECRET_HEADER = "X-Revalidate-Secret";

  private final RestClient http;
  private final WebRevalidateProperties properties;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean pending = new AtomicBoolean();

  @Autowired
  public WebRevalidateClient(
      RestClient webRevalidateRestClient, WebRevalidateProperties properties) {
    this(
        webRevalidateRestClient,
        properties,
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("kurl-web-revalidate").daemon().factory()));
  }

  WebRevalidateClient(
      RestClient http, WebRevalidateProperties properties, ScheduledExecutorService scheduler) {
    this.http = http;
    this.properties = properties;
    this.scheduler = scheduler;
  }

  @Override
  public void requestRevalidation() {
    if (!properties.enabled() || !pending.compareAndSet(false, true)) {
      return;
    }
    try {
      scheduler.schedule(this::send, WINDOW.toMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException closed) {
      pending.set(false);
    }
  }

  void send() {
    pending.set(false);
    try {
      http.post()
          .uri(properties.revalidateUrl())
          .header(SECRET_HEADER, properties.revalidateSecret())
          .contentType(MediaType.APPLICATION_JSON)
          .body("{}")
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException failure) {
      log.warn("web cache revalidation failed: {}", failure.toString());
    }
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }
}
