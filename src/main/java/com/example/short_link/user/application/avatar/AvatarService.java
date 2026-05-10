package com.example.short_link.user.application.avatar;

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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

  /**
   * What we let users actually upload — keeping the list short avoids weird inline content (svg,
   * ico).
   */
  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private final UserRepository userRepository;
  private final AvatarProperties props;
  private final S3Presigner presigner;
  private final S3Client s3Client;

  /**
   * Mints a one-shot presigned PUT URL the browser uses to upload directly to S3, plus the public
   * URL the frontend will render once the upload commits. The key is namespaced under the user's id
   * so on commit we can verify the caller owns it without trusting the client.
   */
  public PresignResult presignUpload(Long userId, String contentType) {
    require(props.isConfigured(), AvatarUnavailableException::new);
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new InvalidAvatarException("contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext;
    PutObjectRequest put =
        PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(normalized).build();
    PutObjectPresignRequest presign =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(props.presignTtlSeconds()))
            .putObjectRequest(put)
            .build();
    PresignedPutObjectRequest signed = presigner.presignPutObject(presign);
    return new PresignResult(
        signed.url().toString(),
        publicUrlFor(key),
        key,
        normalized,
        props.maxBytes(),
        props.presignTtlSeconds());
  }

  /**
   * Confirms the upload, sets {@link UserEntity#getAvatarUrl()}, and best-effort deletes the
   * previous avatar object. Verifies the key is namespaced under {@code avatars/{userId}/} — if the
   * caller's frontend got cute and tried to commit someone else's key it'd fail here.
   */
  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public CommitResult commitUpload(Long userId, String key) {
    require(props.isConfigured(), AvatarUnavailableException::new);
    if (key == null || key.isBlank() || !key.startsWith("avatars/" + userId + "/")) {
      throw new InvalidAvatarException("key not owned by user");
    }
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String previousKey = user.getAvatarKey();
    String publicUrl = publicUrlFor(key);
    user.updateAvatar(publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      // Best-effort: an orphaned object isn't a correctness issue, just cost. Don't fail the commit
      // if S3 hiccups — the user has a working new avatar either way.
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(props.bucket()).key(previousKey).build());
      } catch (Exception e) {
        log.warn("failed to delete previous avatar key={}", previousKey, e);
      }
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
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(props.bucket()).key(previousKey).build());
      } catch (Exception e) {
        log.warn("failed to delete cleared avatar key={}", previousKey, e);
      }
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
