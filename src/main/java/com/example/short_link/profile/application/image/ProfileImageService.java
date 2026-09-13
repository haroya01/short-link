package com.example.short_link.profile.application.image;

import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Commit returns an image URL for a later block update without mutating the block. Uploads may
 * coexist, so a new upload must not delete previous keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private static final String KEY_PREFIX = "profile-images/";

  private final ImageUploadPolicy uploadPolicy;
  private final ObjectStorage objectStorage;
  private final ObjectStoragePublicUrls publicUrls;

  public PresignResult presignUpload(Long userId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(objectStorage.isConfigured());
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new UserException(
          UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(
            key, normalized, Duration.ofSeconds(uploadPolicy.presignTtlSeconds()));
    return new PresignResult(
        uploadUrl,
        publicUrls.forKey(key),
        key,
        normalized,
        uploadPolicy.maxBytes(),
        uploadPolicy.presignTtlSeconds());
  }

  public CommitResult commitUpload(Long userId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(objectStorage.isConfigured());
    String expectedPrefix = KEY_PREFIX + userId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
      throw new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"));
    if (contentLength > uploadPolicy.maxBytes()) {
      try {
        objectStorage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized profile image key={}", key, e);
      }
      throw new UserException(
          UserErrorCode.INVALID_AVATAR,
          "image exceeds maxBytes (" + contentLength + " > " + uploadPolicy.maxBytes() + ")");
    }
    objectStorage.applyImmutableCacheControl(key);
    return new CommitResult(publicUrls.forKey(key), key);
  }

  private static void require(boolean condition) {
    if (!condition) throw new UserException(UserErrorCode.AVATAR_UNAVAILABLE);
  }

  public record PresignResult(
      String uploadUrl,
      String publicUrl,
      String key,
      String contentType,
      long maxBytes,
      long expiresIn) {}

  public record CommitResult(String imageUrl, String key) {}
}
