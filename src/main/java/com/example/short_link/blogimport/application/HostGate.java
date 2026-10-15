package com.example.short_link.blogimport.application;

import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

// At most one request in flight per host from this server; a caller that cannot get its turn within
// hostWait gives up as unreachable instead of queueing without end.
@Component
public class HostGate {

  private final Map<String, Semaphore> hosts = new ConcurrentHashMap<>();
  private final long waitMillis;

  public HostGate(BlogImportProperties properties) {
    this.waitMillis = properties.hostWait().toMillis();
  }

  public <T> T call(String url, Supplier<T> request) {
    Semaphore turn = hosts.computeIfAbsent(host(url), h -> new Semaphore(1, true));
    boolean acquired;
    try {
      acquired = turn.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
    if (!acquired) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
    try {
      return request.get();
    } finally {
      turn.release();
    }
  }

  static String host(String url) {
    try {
      String host = URI.create(url).getHost();
      return host == null ? url : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return url;
    }
  }
}
