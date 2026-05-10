package com.example.short_link.user.application.avatar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 avatar settings. When {@code bucket} is blank we treat the feature as unconfigured — the
 * controller returns 503 so the editor can degrade gracefully (still shows initial-letter avatar).
 *
 * @param bucket Target S3 bucket name (e.g. {@code kurl-avatars-prod}).
 * @param region AWS region (e.g. {@code ap-northeast-2}).
 * @param publicBaseUrl Public read URL prefix (CDN or virtual-host bucket URL). The avatar's {@code
 *     public} URL is {@code publicBaseUrl + "/" + key}. If null we fall back to the standard
 *     virtual-host S3 URL — useful when not fronting with a CDN.
 * @param presignTtlSeconds How long the presigned PUT URL stays valid; short on purpose so a leaked
 *     link can't be replayed forever.
 * @param maxBytes Hard upper bound on avatar size; the frontend enforces this and the bucket policy
 *     should mirror it (S3 presigned PUT itself can't enforce object-size).
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
