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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Profile banner / hero image upload. Mirrors {@link AvatarService} but writes under {@code
 * banners/{userId}/...} and updates {@link UserEntity#getBannerUrl()}. Reuses the same S3 bucket /
 * IAM / CloudFront setup — only the key prefix differs.
 *
 * <p>Banners are wider than avatars (3:1 aspect on the public profile) so the {@code maxBytes}
 * inherited from {@link AvatarProperties} should accommodate up to ~2K JPEG. The frontend's resize
 * step keeps payloads small regardless.
 */
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
  private final AvatarProperties props;
  private final S3Presigner presigner;
  private final S3Client s3Client;

  public PresignResult presignUpload(Long userId, String contentType) {
    require(props.isConfigured());
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new InvalidAvatarException("contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = "banners/" + userId + "/" + UUID.randomUUID() + "." + ext;
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

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public CommitResult commitUpload(Long userId, String key) {
    require(props.isConfigured());
    if (key == null || key.isBlank() || !key.startsWith("banners/" + userId + "/")) {
      throw new InvalidAvatarException("key not owned by user");
    }
    HeadObjectResponse head;
    try {
      head =
          s3Client.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
    } catch (Exception e) {
      throw new InvalidAvatarException("upload not found");
    }
    Long contentLength = head.contentLength();
    if (contentLength != null && contentLength > props.maxBytes()) {
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
      } catch (Exception e) {
        log.warn("failed to delete oversized banner key={}", key, e);
      }
      throw new InvalidAvatarException(
          "banner exceeds maxBytes (" + contentLength + " > " + props.maxBytes() + ")");
    }
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String previousKey = user.getBannerKey();
    String publicUrl = publicUrlFor(key);
    user.updateBanner(publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(props.bucket()).key(previousKey).build());
      } catch (Exception e) {
        log.warn("failed to delete previous banner key={}", previousKey, e);
      }
    }
    return new CommitResult(publicUrl);
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void clearBanner(Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String previousKey = user.getBannerKey();
    user.updateBanner(null, null);
    if (props.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      try {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(props.bucket()).key(previousKey).build());
      } catch (Exception e) {
        log.warn("failed to delete cleared banner key={}", previousKey, e);
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

  private static void require(boolean condition) {
    if (!condition) throw new AvatarUnavailableException();
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
