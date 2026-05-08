package com.example.short_link.common.geoip;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Optional holder for the GeoLite2-ASN reader. Unlike the city holder, this one is allowed to stay
 * empty — if no ASN db is bundled or downloaded, ASN resolution is simply skipped (returns null org
 * / 0 asn). This keeps the app bootable in dev without requiring a MaxMind license key.
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
