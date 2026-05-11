package com.example.short_link.profile.image;

import com.example.short_link.user.application.avatar.AvatarProperties;
import com.example.short_link.user.application.avatar.AvatarUnavailableException;
import com.example.short_link.user.application.avatar.InvalidAvatarException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Generic user-uploaded image storage for profile blocks (gallery, product cards, etc.). Mirrors
 * {@link com.example.short_link.user.application.avatar.BannerService} but writes under {@code
 * profile-images/{userId}/...}, and does <b>not</b> mutate any entity on commit — the resulting URL
 * is meant to be embedded into a block's JSON content via the existing block update endpoint.
 * Multiple uploads per user are expected (gallery is up to 6 images, product cards may have several
 * too), so we never delete a previous key on a new upload.
 *
 * <p>Why a separate service instead of extending BannerService:
 *
 * <ul>
 *   <li>Banner / avatar have a single "current" URL tied to the user — commit updates that field +
 *       deletes the previous S3 key. Profile-block images don't have that lifecycle.
 *   <li>Key prefix differs ({@code profile-images/} vs {@code banners/} vs {@code avatars/}) so IAM
 *       bucket policies can scope by prefix if we ever need to.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

  /** Content-type whitelist mirrors avatar/banner: same input formats, same output bucket. */
  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private static final String KEY_PREFIX = "profile-images/";

  private final AvatarProperties props;
  private final S3Presigner presigner;
  private final S3Client s3Client;

  /**
   * Hands back a presigned PUT URL the client can upload directly to. The frontend should resize +
   * size-cap before upload, since S3 itself cannot enforce object size on a presigned PUT; we do a
   * HEAD check on commit as the server-side gate.
   *
   * @throws AvatarUnavailableException when S3 is not configured (returns 503 upstream).
   * @throws InvalidAvatarException when {@code contentType} isn't in the whitelist.
   */
  public PresignResult presignUpload(Long userId, String contentType) {
    if (userId == null) throw new InvalidAvatarException("userId required");
    require(props.isConfigured());
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new InvalidAvatarException("contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + ext;
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
   * Verifies the just-uploaded object exists, belongs to the caller (by key prefix check), and is
   * within the size cap. Returns the public CDN URL the client should persist into the block JSON.
   *
   * <p>This is the server-side enforcement point for the size limit — frontend resize is a UX
   * nicety, not a security boundary. If the HEAD shows oversize, we delete the object so we don't
   * silently keep abandoned data in the bucket.
   *
   * @throws InvalidAvatarException when the key is not owned by the user, the object is missing, or
   *     it exceeds {@link AvatarProperties#maxBytes()}.
   */
  public CommitResult commitUpload(Long userId, String key) {
    if (userId == null) throw new InvalidAvatarException("userId required");
    require(props.isConfigured());
    String expectedPrefix = KEY_PREFIX + userId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
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
        log.warn("failed to delete oversized profile image key={}", key, e);
      }
      throw new InvalidAvatarException(
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
    if (!condition) throw new AvatarUnavailableException();
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
