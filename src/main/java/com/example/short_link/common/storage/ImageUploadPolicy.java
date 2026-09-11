package com.example.short_link.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared image upload limits, independent of the storage provider. The legacy avatar prefix
 * preserves existing deployment settings for every image feature.
 */
@ConfigurationProperties(prefix = "short-link.avatar")
public record ImageUploadPolicy(long presignTtlSeconds, long maxBytes) {

  public ImageUploadPolicy {
    if (presignTtlSeconds <= 0) presignTtlSeconds = 300;
    if (maxBytes <= 0) maxBytes = 5L * 1024 * 1024;
  }
}
