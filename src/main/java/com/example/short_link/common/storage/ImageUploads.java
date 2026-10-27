package com.example.short_link.common.storage;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUploads {

  public static final Map<String, String> STILL =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  public static final Map<String, String> STILL_OR_GIF =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/gif", "gif");

  private final ImageUploadPolicy policy;
  private final ObjectStorage storage;
  private final ObjectStoragePublicUrls publicUrls;

  public void requireConfigured(ImageRejections rejections) {
    if (!storage.isConfigured()) {
      throw rejections.unconfigured().get();
    }
  }

  public PresignedUpload presign(
      String ownedPrefix,
      String contentType,
      Map<String, String> allowedTypes,
      ImageRejections rejections) {
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = allowedTypes.get(normalized);
    if (ext == null) {
      throw rejections.unsupportedType().apply(allowedTypes.keySet());
    }
    String key = ownedPrefix + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        storage.presignPut(key, normalized, Duration.ofSeconds(policy.presignTtlSeconds()));
    return new PresignedUpload(
        uploadUrl,
        publicUrls.forKey(key),
        key,
        normalized,
        policy.maxBytes(),
        policy.presignTtlSeconds());
  }

  public String commit(String ownedPrefix, String key, ImageRejections rejections) {
    return commit(ownedPrefix, key, rejections, storage::delete);
  }

  public String commitWithVariants(String ownedPrefix, String key, ImageRejections rejections) {
    return commit(ownedPrefix, key, rejections, storage::deleteWithVariants);
  }

  private String commit(
      String ownedPrefix, String key, ImageRejections rejections, Consumer<String> deleter) {
    if (key == null || key.isBlank() || !key.startsWith(ownedPrefix)) {
      throw rejections.notOwned().get();
    }
    long size = storage.objectSize(key).orElseThrow(rejections.missing());
    if (size > policy.maxBytes()) {
      try {
        deleter.accept(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized image key={}", key, e);
      }
      throw rejections.oversized().apply(size, policy.maxBytes());
    }
    storage.applyImmutableCacheControl(key);
    return publicUrls.forKey(key);
  }
}
