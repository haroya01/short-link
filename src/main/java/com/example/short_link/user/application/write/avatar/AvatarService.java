package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.profile.application.ProfileCacheEviction;
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
public class AvatarService {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private final UserRepository userRepository;
  private final AvatarProperties props;
  private final ObjectStorage objectStorage;
  private final ProfileCacheEviction cacheEviction;

  public PresignResult presignUpload(Long userId, String contentType) {
    require(props.isConfigured(), () -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new UserException(
          UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(key, normalized, Duration.ofSeconds(props.presignTtlSeconds()));
    return new PresignResult(
        uploadUrl, publicUrlFor(key), key, normalized, props.maxBytes(), props.presignTtlSeconds());
  }

  @Transactional
  public CommitResult commitUpload(Long userId, String key) {
    require(props.isConfigured(), () -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (key == null || key.isBlank() || !key.startsWith("avatars/" + userId + "/")) {
      throw new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"));
    if (contentLength > props.maxBytes()) {
      deleteQuietly(key, "oversized avatar");
      throw new UserException(
          UserErrorCode.INVALID_AVATAR,
          "avatar exceeds maxBytes (" + contentLength + " > " + props.maxBytes() + ")");
    }
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String previousKey = user.getAvatarKey();
    String publicUrl = publicUrlFor(key);
    user.updateAvatar(publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      deleteQuietly(previousKey, "previous avatar");
    }
    cacheEviction.evictByUsername(user.getUsername());
    return new CommitResult(publicUrl);
  }

  @Transactional
  public void clearAvatar(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String previousKey = user.getAvatarKey();
    user.updateAvatar(null, null);
    if (props.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      deleteQuietly(previousKey, "cleared avatar");
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

  private String publicUrlFor(String key) {
    String base = props.publicBaseUrl();
    if (base == null || base.isBlank()) {
      base = "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com";
    }
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + "/" + key;
  }

  private static <T extends RuntimeException> void require(
      boolean condition, java.util.function.Supplier<T> exception) {
    if (!condition) throw exception.get();
  }

  public record PresignResult(
      String uploadUrl,
      String publicUrl,
      String key,
      String contentType,
      long maxBytes,
      long expiresIn) {}

  public record CommitResult(String avatarUrl) {}
}
