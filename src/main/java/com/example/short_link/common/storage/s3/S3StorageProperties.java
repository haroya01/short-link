package com.example.short_link.common.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared S3 location. The legacy avatar prefix preserves existing deployment settings. */
@ConfigurationProperties(prefix = "short-link.avatar")
public record S3StorageProperties(String bucket, String region, String publicBaseUrl) {

  public boolean isConfigured() {
    return bucket != null && !bucket.isBlank() && region != null && !region.isBlank();
  }
}
