package com.example.short_link.common.geoip;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hot-swappable holder around the GeoLite2 {@link DatabaseReader}. The startup config seeds it from
 * the bundled fallback mmdb; a scheduled refresh job can {@link #set(DatabaseReader)} a freshly
 * downloaded reader without restarting the app. The previous reader is closed shortly after — any
 * in-flight {@code tryCity()} calls will see the new reader on the next invocation.
 */
@Slf4j
@Component
public class GeoIpDatabaseHolder {

  private final AtomicReference<DatabaseReader> ref = new AtomicReference<>();

  public DatabaseReader get() {
    DatabaseReader current = ref.get();
    if (current == null) {
      throw new IllegalStateException("GeoIp database not initialised");
    }
    return current;
  }

  public void set(DatabaseReader next) {
    DatabaseReader previous = ref.getAndSet(next);
    if (previous != null) {
      try {
        previous.close();
      } catch (IOException e) {
        log.warn("failed to close previous GeoIp database reader", e);
      }
    }
  }

  @PreDestroy
  void onShutdown() {
    DatabaseReader current = ref.getAndSet(null);
    if (current != null) {
      try {
        current.close();
      } catch (IOException e) {
        log.warn("failed to close GeoIp database reader on shutdown", e);
      }
    }
  }
}
