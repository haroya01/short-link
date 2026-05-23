package com.example.short_link.user.application.avatar;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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

  public PresignResult presignUpload(Long userId, String contentType) {
    require(props.isConfigured(), AvatarUnavailableException::new);
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new InvalidAvatarException("contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(key, normalized, Duration.ofSeconds(props.presignTtlSeconds()));
    return new PresignResult(
        uploadUrl, publicUrlFor(key), key, normalized, props.maxBytes(), props.presignTtlSeconds());
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public CommitResult commitUpload(Long userId, String key) {
    require(props.isConfigured(), AvatarUnavailableException::new);
    if (key == null || key.isBlank() || !key.startsWith("avatars/" + userId + "/")) {
      throw new InvalidAvatarException("key not owned by user");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(() -> new InvalidAvatarException("upload not found"));
    if (contentLength > props.maxBytes()) {
      deleteQuietly(key, "oversized avatar");
      throw new InvalidAvatarException(
          "avatar exceeds maxBytes (" + contentLength + " > " + props.maxBytes() + ")");
    }
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String previousKey = user.getAvatarKey();
    String publicUrl = publicUrlFor(key);
    user.updateAvatar(publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      deleteQuietly(previousKey, "previous avatar");
    }
    return new CommitResult(publicUrl);
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void clearAvatar(Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String previousKey = user.getAvatarKey();
    user.updateAvatar(null, null);
    if (props.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      deleteQuietly(previousKey, "cleared avatar");
    }
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
