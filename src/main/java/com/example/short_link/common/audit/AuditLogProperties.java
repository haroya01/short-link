package com.example.short_link.common.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.audit-log")
public record AuditLogProperties(long retentionDays, boolean cleanupEnabled) {

  public AuditLogProperties {
    if (retentionDays <= 0) retentionDays = 180;
  }
}
