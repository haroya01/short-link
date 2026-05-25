package com.example.short_link.common.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 avatar settings. When {@code bucket} is blank we treat the feature as unconfigured — the
 * controller returns 503 so the editor can degrade gracefully (still shows initial-letter avatar).
 */
@ConfigurationProperties(prefix = "short-link.avatar")
public record AvatarProperties(
    String bucket, String region, String publicBaseUrl, long presignTtlSeconds, long maxBytes) {

  public AvatarProperties {
    if (presignTtlSeconds <= 0) presignTtlSeconds = 300;
    if (maxBytes <= 0) maxBytes = 5L * 1024 * 1024;
  }

  public boolean isConfigured() {
    return bucket != null && !bucket.isBlank() && region != null && !region.isBlank();
  }
}
