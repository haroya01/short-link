package com.example.short_link.post.application.image;

import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
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
 * <p>{@link #importFromUrl}은 외부 URL(노션 등에서 붙여넣은 이미지)을 서버가 직접 받아 우리 버킷에 재호스팅한다 — 노션 서명 URL 은 만료되므로
 * 핫링크하면 발행 후 깨지기 때문. 외부 URL과 응답 검증은 {@link ExternalPostImageReader}가 담당한다.
 *
 * <p>업로드 용량·서명 유효 시간은 공통 ImageUploadPolicy를 따른다. 저장소 연결과 공개 주소는 별도 협력자가 담당한다.
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

  private final ImageUploadPolicy uploadPolicy;
  private final ObjectStorage objectStorage;
  private final PostOwnership postOwnership;
  private final ExternalPostImageReader externalImages;
  private final ObjectStoragePublicUrls publicUrls;

  public PresignResult presignUpload(Long userId, Long postId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(objectStorage.isConfigured());
    postOwnership.verifyOwned(userId, postId);
    String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    String ext = ALLOWED_TYPES.get(normalized);
    if (ext == null) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED, "contentType must be one of: " + ALLOWED_TYPES.keySet());
    }
    String key = KEY_PREFIX + userId + "/" + postId + "/" + UUID.randomUUID() + "." + ext;
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

  public CommitResult commitUpload(Long userId, Long postId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(objectStorage.isConfigured());
    postOwnership.verifyOwned(userId, postId);
    String expectedPrefix = KEY_PREFIX + userId + "/" + postId + "/";
    if (key == null || key.isBlank() || !key.startsWith(expectedPrefix)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED, "key not owned by user/post");
    }
    long contentLength =
        objectStorage
            .objectSize(key)
            .orElseThrow(
                () -> new PostException(PostErrorCode.PERMISSION_DENIED, "upload not found"));
    if (contentLength > uploadPolicy.maxBytes()) {
      try {
        objectStorage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete oversized post image key={}", key, e);
      }
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
          "image exceeds maxBytes (" + contentLength + " > " + uploadPolicy.maxBytes() + ")");
    }
    objectStorage.applyImmutableCacheControl(key);
    return new CommitResult(publicUrls.forKey(key), key);
  }

  /**
   * Server-side re-host: fetch an external image URL through the SSRF guard, validate it's a real
   * image within the size cap, and store it in our bucket. Returns the same shape as {@link
   * #commitUpload} so the editor inserts a kurl-owned URL that won't expire (unlike a hotlinked 노션
   * 서명 URL).
   */
  public CommitResult importFromUrl(Long userId, Long postId, String url) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(objectStorage.isConfigured());
    postOwnership.verifyOwned(userId, postId);

    ExternalPostImageReader.Image image = externalImages.read(url, uploadPolicy.maxBytes(), postId);
    String contentType = image.contentType();
    byte[] body = image.body();

    String key =
        KEY_PREFIX
            + userId
            + "/"
            + postId
            + "/"
            + UUID.randomUUID()
            + "."
            + ALLOWED_TYPES.get(contentType);
    objectStorage.putObject(key, contentType, body);
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
