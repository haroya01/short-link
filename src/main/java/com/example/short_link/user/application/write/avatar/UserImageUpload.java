package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.common.storage.PresignedUpload;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class UserImageUpload {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private final String folder;
  private final String label;
  private final UserRepository userRepository;
  private final ImageUploadPolicy uploadPolicy;
  private final ObjectStorage objectStorage;
  private final ObjectStoragePublicUrls publicUrls;
  private final ProfileCacheInvalidator cacheEviction;

  protected UserImageUpload(
      String folder,
      String label,
      UserRepository userRepository,
      ImageUploadPolicy uploadPolicy,
      ObjectStorage objectStorage,
      ObjectStoragePublicUrls publicUrls,
      ProfileCacheInvalidator cacheEviction) {
    this.folder = folder;
    this.label = label;
    this.userRepository = userRepository;
    this.uploadPolicy = uploadPolicy;
    this.objectStorage = objectStorage;
    this.publicUrls = publicUrls;
    this.cacheEviction = cacheEviction;
  }

  protected abstract String currentKey(UserEntity user);

  protected abstract void replace(UserEntity user, String url, String key);

  public PresignedUpload presignUpload(Long userId, String contentType) {
    requireConfigured();
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new UserException(
          UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = folder + "/" + userId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(
            key, normalized, Duration.ofSeconds(uploadPolicy.presignTtlSeconds()));
    return new PresignedUpload(
        uploadUrl,
        publicUrls.forKey(key),
        key,
        normalized,
        uploadPolicy.maxBytes(),
        uploadPolicy.presignTtlSeconds());
  }

  String commit(Long userId, String key) {
    requireConfigured();
    if (key == null || key.isBlank() || !key.startsWith(folder + "/" + userId + "/")) {
      throw new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"));
    if (contentLength > uploadPolicy.maxBytes()) {
      deleteQuietly(key, "oversized " + label);
      throw new UserException(
          UserErrorCode.INVALID_AVATAR,
          label + " exceeds maxBytes (" + contentLength + " > " + uploadPolicy.maxBytes() + ")");
    }
    objectStorage.applyImmutableCacheControl(key);
    UserEntity user = user(userId);
    String previousKey = currentKey(user);
    String publicUrl = publicUrls.forKey(key);
    replace(user, publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      deleteQuietly(previousKey, "previous " + label);
    }
    cacheEviction.evictByUsername(user.getUsername());
    return publicUrl;
  }

  void clear(Long userId) {
    UserEntity user = user(userId);
    String previousKey = currentKey(user);
    replace(user, null, null);
    if (objectStorage.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      deleteQuietly(previousKey, "cleared " + label);
    }
    cacheEviction.evictByUsername(user.getUsername());
  }

  private UserEntity user(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }

  private void requireConfigured() {
    if (!objectStorage.isConfigured()) {
      throw new UserException(UserErrorCode.AVATAR_UNAVAILABLE);
    }
  }

  private void deleteQuietly(String key, String what) {
    try {
      objectStorage.delete(key);
    } catch (ObjectStorageException e) {
      log.warn("failed to delete {} key={}", what, key, e);
    }
  }
}
