package com.example.short_link.federation.application.delivery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.delivery")
public record DeliveryProperties(
    Boolean enabled, int batchSize, Duration lease, Duration giveUpAfter, Duration retention) {

  public DeliveryProperties {
    enabled = enabled == null || enabled;
    batchSize = batchSize <= 0 ? 20 : batchSize;
    lease = lease == null ? Duration.ofMinutes(2) : lease;
    giveUpAfter = giveUpAfter == null ? Duration.ofDays(2) : giveUpAfter;
    retention = retention == null ? Duration.ofDays(7) : retention;
  }
}
