package com.example.short_link.link.health.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.destination-health")
public record DestinationHealthProperties(
    boolean enabled, int batchSize, int recheckHours, int confirmAfter) {

  public DestinationHealthProperties {
    if (batchSize <= 0) {
      batchSize = 60;
    }
    if (recheckHours <= 0) {
      recheckHours = 20;
    }
    if (confirmAfter <= 0) {
      confirmAfter = 2;
    }
  }
}
