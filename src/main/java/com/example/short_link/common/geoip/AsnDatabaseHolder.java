package com.example.short_link.common.geoip;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * May remain empty when no ASN database is available; lookups then return null organization / 0
 * ASN.
 */
@Slf4j
@Component
public class AsnDatabaseHolder {

  private final AtomicReference<DatabaseReader> ref = new AtomicReference<>();

  public DatabaseReader getOrNull() {
    return ref.get();
  }

  public void set(DatabaseReader next) {
    DatabaseReader previous = ref.getAndSet(next);
    if (previous != null) {
      try {
        previous.close();
      } catch (IOException e) {
        log.warn("failed to close previous ASN database reader", e);
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
        log.warn("failed to close ASN database reader on shutdown", e);
      }
    }
  }
}
