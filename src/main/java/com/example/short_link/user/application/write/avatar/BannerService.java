package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private final UserRepository userRepository;
  private final ImageUploadPolicy uploadPolicy;
  private final ObjectStorage objectStorage;
  private final ObjectStoragePublicUrls publicUrls;
  private final ProfileCacheInvalidator cacheEviction;

  public PresignResult presignUpload(Long userId, String contentType) {
    require(objectStorage.isConfigured());
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new UserException(
          UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = "banners/" + userId + "/" + UUID.randomUUID() + "." + ext;
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

  @Transactional
  public CommitResult commitUpload(Long userId, String key) {
    require(objectStorage.isConfigured());
    if (key == null || key.isBlank() || !key.startsWith("banners/" + userId + "/")) {
      throw new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"));
    if (contentLength > uploadPolicy.maxBytes()) {
      deleteQuietly(key, "oversized banner");
      throw new UserException(
          UserErrorCode.INVALID_AVATAR,
          "banner exceeds maxBytes (" + contentLength + " > " + uploadPolicy.maxBytes() + ")");
    }
    objectStorage.applyImmutableCacheControl(key);
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String previousKey = user.getBannerKey();
    String publicUrl = publicUrls.forKey(key);
    user.updateBanner(publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      deleteQuietly(previousKey, "previous banner");
    }
    cacheEviction.evictByUsername(user.getUsername());
    return new CommitResult(publicUrl);
  }

  @Transactional
  public void clearBanner(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String previousKey = user.getBannerKey();
    user.updateBanner(null, null);
    if (objectStorage.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      deleteQuietly(previousKey, "cleared banner");
    }
    cacheEviction.evictByUsername(user.getUsername());
  }

  private void deleteQuietly(String key, String label) {
    try {
      objectStorage.delete(key);
    } catch (ObjectStorageException e) {
      log.warn("failed to delete {} key={}", label, key, e);
    }
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

  public record CommitResult(String bannerUrl) {}
}
