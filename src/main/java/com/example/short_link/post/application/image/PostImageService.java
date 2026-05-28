package com.example.short_link.post.application.image;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
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
 * Post 본문 IMAGE 블록용 이미지 업로드. ProfileImageService 패턴 정합 — presign / commit two-step. key prefix 는
 * {@code post-images/{userId}/{postId}/{uuid}.{ext}}. 글 소유권 검증 PostOwnership 통해.
 *
 * <p>AvatarProperties 의 maxBytes / presignTtlSeconds / bucket 재사용 (별도 prop 그룹 안 만들고 동일 storage 정책
 * 적용).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {

  private static final Map<String, String> ALLOWED_TYPES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/gif", "gif");

  private static final String KEY_PREFIX = "post-images/";

  private final AvatarProperties props;
  private final ObjectStorage objectStorage;
  private final PostOwnership postOwnership;

  public PresignResult presignUpload(Long userId, Long postId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(props.isConfigured());
    postOwnership.requireOwned(userId, postId);
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + postId + "/" + UUID.randomUUID() + "." + ext;
    String uploadUrl =
        objectStorage.presignPut(key, normalized, Duration.ofSeconds(props.presignTtlSeconds()));
    return new PresignResult(
        uploadUrl, publicUrlFor(key), key, normalized, props.maxBytes(), props.presignTtlSeconds());
  }

  public CommitResult commitUpload(Long userId, Long postId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(props.isConfigured());
    postOwnership.requireOwned(userId, postId);
    String expectedPrefix = KEY_PREFIX + userId + "/" + postId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED, "key not owned by user/post");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(
                () -> new PostException(PostErrorCode.PERMISSION_DENIED, "upload not found"));
    if (contentLength > props.maxBytes()) {
      try {
        objectStorage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized post image key={}", key, e);
      }
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
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
