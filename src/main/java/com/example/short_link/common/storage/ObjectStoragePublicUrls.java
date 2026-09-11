package com.example.short_link.common.storage;

import com.example.short_link.common.storage.s3.S3StorageProperties;
import org.springframework.stereotype.Component;

/** Builds public image addresses independently of upload and ownership operations. */
@Component
public final class ObjectStoragePublicUrls {

  private final String baseUrl;

  public ObjectStoragePublicUrls(S3StorageProperties properties) {
    String configuredBase = properties.publicBaseUrl();
    String base =
        configuredBase == null || configuredBase.isBlank()
            ? "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com"
            : configuredBase;
    this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  public String forKey(String key) {
    return key == null || key.isBlank() ? null : baseUrl + "/" + key;
  }
}
