package com.example.short_link.federation.application.relay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.relay")
public record RelayProperties(
    boolean enabled, int perMinute, int perDay, int queue, Duration patience, Duration slowAfter) {

  public RelayProperties {
    perMinute = perMinute <= 0 ? 30 : perMinute;
    perDay = perDay <= 0 ? 2_000 : perDay;
    queue = queue <= 0 ? 500 : queue;
    patience = patience == null ? Duration.ofSeconds(5) : patience;
    slowAfter = slowAfter == null ? Duration.ofSeconds(4) : slowAfter;
  }
}
