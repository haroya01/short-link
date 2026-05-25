package com.example.short_link.profile.application.image;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
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
 * Generic user-uploaded image storage for profile blocks (gallery, product cards, etc.). Mirrors
 * {@link com.example.short_link.user.application.avatar.BannerService} but writes under {@code
 * profile-images/{userId}/...}, and does <b>not</b> mutate any entity on commit — the resulting URL
 * is meant to be embedded into a block's JSON content via the existing block update endpoint.
 * Multiple uploads per user are expected (gallery is up to 6 images, product cards may have several
 * too), so we never delete a previous key on a new upload.
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

  private final AvatarProperties props;
  private final ObjectStorage objectStorage;

  public PresignResult presignUpload(Long userId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(props.isConfigured());
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new UserException(
          UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(key, normalized, Duration.ofSeconds(props.presignTtlSeconds()));
    return new PresignResult(
        uploadUrl, publicUrlFor(key), key, normalized, props.maxBytes(), props.presignTtlSeconds());
  }

  public CommitResult commitUpload(Long userId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(props.isConfigured());
    String expectedPrefix = KEY_PREFIX + userId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
      throw new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"));
    if (contentLength > props.maxBytes()) {
      try {
        objectStorage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized profile image key={}", key, e);
      }
      throw new UserException(
          UserErrorCode.INVALID_AVATAR,
          "image exceeds maxBytes (" + contentLength + " > " + props.maxBytes() + ")");
    }
    return new CommitResult(publicUrlFor(key), key);
  }

  private String publicUrlFor(String key) {
    String base = props.publicBaseUrl();
    if (base == null || base.isBlank()) {
      base = "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com";
    }
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + "/" + key;
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
