package com.example.short_link.post.application.image;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
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
 * 핫링크하면 발행 후 깨지기 때문. 아웃바운드 호출은 OG 스크래퍼와 동일한 SSRF-safe {@link HttpFetcher}/{@link
 * PublicHttpUrlGuard} 를 거친다.
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

  private static final Duration IMPORT_CONNECT_TIMEOUT = Duration.ofMillis(3_000);
  private static final Duration IMPORT_READ_TIMEOUT = Duration.ofMillis(5_000);
  private static final String IMPORT_USER_AGENT = "kurl-image-import/1.0 (+https://kurl.me/bot)";
  private static final int MAX_REDIRECT_HOPS = 5;

  private final AvatarProperties props;
  private final ObjectStorage objectStorage;
  private final PostOwnership postOwnership;
  private final HttpFetcher httpFetcher;

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

  /**
   * Server-side re-host: fetch an external image URL through the SSRF guard, validate it's a real
   * image within the size cap, and store it in our bucket. Returns the same shape as {@link
   * #commitUpload} so the editor inserts a kurl-owned URL that won't expire (unlike a hotlinked 노션
   * 서명 URL).
   */
  public CommitResult importFromUrl(Long userId, Long postId, String url) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    require(props.isConfigured());
    postOwnership.requireOwned(userId, postId);

    Resolved resolved =
        PublicHttpUrlGuard.resolve(url == null ? null : url.trim())
            .orElseThrow(
                () ->
                    new PostException(
                        PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + url));

    // 캡보다 1바이트 더 받아오게 해서, 잘려서 정확히 maxBytes 인 경우와 진짜 초과를 구분한다.
    long maxBytes = props.maxBytes();
    int fetchCap = (int) Math.min(maxBytes + 1, Integer.MAX_VALUE);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", IMPORT_USER_AGENT);
    headers.put("Accept", "image/*");

    Response response = fetchFollowingRedirects(resolved, headers, fetchCap, url, postId);
    if (response.status() >= 400) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED, "image fetch returned " + response.status());
    }
    byte[] body = response.body();
    if (body.length == 0) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED, "empty image body");
    }
    String contentType = sniffImageType(body);
    if (contentType == null) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
          "fetched bytes are not a supported image (jpeg/png/webp/gif)");
    }
    if (body.length > maxBytes) {
      throw new PostException(
          PostErrorCode.PERMISSION_DENIED,
          "image exceeds maxBytes (" + body.length + " > " + maxBytes + ")");
    }

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
    return new CommitResult(publicUrlFor(key), key);
  }

  /**
   * 리다이렉트를 hop 단위로 직접 따라간다. Pinned DNS 는 cross-host 리다이렉트를 죽이는데(DNS 리바인딩 방어), 노션 이미지
   * 프록시(www.notion.so/image/...)는 S3 서명 URL 로 302 를 주므로 자동 추적으로는 항상 실패했다. 각 hop 의 Location 을 {@link
   * PublicHttpUrlGuard} 로 다시 해석-검증해 사설 IP 로 새는 것을 막는다.
   */
  private Response fetchFollowingRedirects(
      Resolved first, Map<String, String> headers, int fetchCap, String originalUrl, Long postId) {
    Resolved current = first;
    for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
      Response response;
      try {
        response =
            httpFetcher.fetch(
                Request.getNoRedirects(
                    current, headers, IMPORT_CONNECT_TIMEOUT, IMPORT_READ_TIMEOUT, fetchCap));
      } catch (RuntimeException e) {
        log.warn("post image import fetch failed url={} post={}", originalUrl, postId, e);
        throw new PostException(PostErrorCode.PERMISSION_DENIED, "image fetch failed");
      }
      if (!isRedirect(response.status())) {
        return response;
      }
      String location = response.header("Location");
      if (location == null || location.isBlank()) {
        throw new PostException(PostErrorCode.PERMISSION_DENIED, "redirect missing location");
      }
      URI next;
      try {
        next = current.uri().resolve(location.trim());
      } catch (IllegalArgumentException e) {
        throw new PostException(
            PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + location);
      }
      current =
          PublicHttpUrlGuard.resolve(next.toString())
              .orElseThrow(
                  () ->
                      new PostException(
                          PostErrorCode.PERMISSION_DENIED, "image url not allowed: " + next));
    }
    throw new PostException(PostErrorCode.PERMISSION_DENIED, "too many redirects");
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  /**
   * 바이트 시그니처로 이미지 타입을 판별한다. 원격 서버의 Content-Type 은 공격자 통제 값이고 정상 케이스에서도 자주 틀리므로(노션/S3 서명 URL 이
   * {@code application/octet-stream} 으로 내려주는 경우가 흔함) 저장 타입은 바이트에서 얻는다 — 시그니처가 허용 타입과 안 맞으면
   * (HTML/SVG 폴리글랏 포함) 거부된다.
   */
  private static String sniffImageType(byte[] b) {
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (b.length >= 8
        && (b[0] & 0xFF) == 0x89
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && (b[4] & 0xFF) == 0x0D
        && (b[5] & 0xFF) == 0x0A
        && (b[6] & 0xFF) == 0x1A
        && (b[7] & 0xFF) == 0x0A) {
      return "image/png";
    }
    if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
      return "image/gif";
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return "image/webp";
    }
    return null;
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
