package com.example.short_link.post.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

  private static final String IMG_URL = "https://cdn.notion.so/secure/abc.png";

  private static final byte[] PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  @Mock private AvatarProperties props;
  @Mock private ObjectStorage objectStorage;
  @Mock private PostOwnership postOwnership;
  @Mock private HttpFetcher httpFetcher;

  private PostImageService service;

  @BeforeEach
  void setUp() {
    service = new PostImageService(props, objectStorage, postOwnership, httpFetcher);
  }

  private static HttpFetcher.Response response(int status, String contentType, byte[] body) {
    return new HttpFetcher.Response(status, Map.of("Content-Type", List.of(contentType)), body);
  }

  private static HttpFetcher.Response redirect(int status, String location) {
    return new HttpFetcher.Response(status, Map.of("Location", List.of(location)), new byte[0]);
  }

  private CommitResultRunner whenImport() {
    return new CommitResultRunner();
  }

  /**
   * Stubs the SSRF guard (static) so importFromUrl runs without real DNS, like OgScraperFetchTest.
   */
  private final class CommitResultRunner {
    PostImageService.CommitResult run() {
      try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
        Resolved resolved = new Resolved(URI.create(IMG_URL), List.<InetAddress>of());
        guard.when(() -> PublicHttpUrlGuard.resolve(IMG_URL)).thenReturn(Optional.of(resolved));
        return service.importFromUrl(7L, 42L, IMG_URL);
      }
    }

    void runExpectingThrow() {
      try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
        Resolved resolved = new Resolved(URI.create(IMG_URL), List.<InetAddress>of());
        guard.when(() -> PublicHttpUrlGuard.resolve(IMG_URL)).thenReturn(Optional.of(resolved));
        assertThatThrownBy(() -> service.importFromUrl(7L, 42L, IMG_URL))
            .isInstanceOf(PostException.class)
            .extracting(e -> ((PostException) e).errorCode())
            .isEqualTo(PostErrorCode.PERMISSION_DENIED);
      }
    }
  }

  @Test
  void presignReturnsUploadUrl() {
    when(props.isConfigured()).thenReturn(true);
    when(props.presignTtlSeconds()).thenReturn(60L);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(objectStorage.presignPut(anyString(), anyString(), any()))
        .thenReturn("https://s3.example/presigned");

    PostImageService.PresignResult result = service.presignUpload(7L, 42L, "image/png");

    assertThat(result.uploadUrl()).isEqualTo("https://s3.example/presigned");
    assertThat(result.key()).startsWith("post-images/7/42/").endsWith(".png");
    assertThat(result.publicUrl()).startsWith("https://cdn.kurl.me/post-images/7/42/");
    assertThat(result.maxBytes()).isEqualTo(5_000_000L);
  }

  @Test
  void presignRejectsInvalidContentType() {
    when(props.isConfigured()).thenReturn(true);
    assertThatThrownBy(() -> service.presignUpload(7L, 42L, "application/pdf"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void presignFailsWhenStorageNotConfigured() {
    when(props.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> service.presignUpload(7L, 42L, "image/png"))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.AVATAR_UNAVAILABLE);
  }

  @Test
  void commitChecksOwnershipAndSize() {
    when(props.isConfigured()).thenReturn(true);
    when(objectStorage.objectSize("post-images/7/42/uuid.png")).thenReturn(Optional.of(1_000L));
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");

    PostImageService.CommitResult result =
        service.commitUpload(7L, 42L, "post-images/7/42/uuid.png");

    assertThat(result.imageUrl()).isEqualTo("https://cdn.kurl.me/post-images/7/42/uuid.png");
  }

  @Test
  void commitRejectsForeignKey() {
    when(props.isConfigured()).thenReturn(true);
    assertThatThrownBy(() -> service.commitUpload(7L, 42L, "post-images/9/99/uuid.png"))
        .isInstanceOf(PostException.class);
  }

  @Test
  void commitRejectsOversize() {
    when(props.isConfigured()).thenReturn(true);
    when(objectStorage.objectSize("post-images/7/42/uuid.png"))
        .thenReturn(Optional.of(10_000_000L));
    when(props.maxBytes()).thenReturn(5_000_000L);

    assertThatThrownBy(() -> service.commitUpload(7L, 42L, "post-images/7/42/uuid.png"))
        .isInstanceOf(PostException.class);
  }

  @Test
  void importRehostsExternalImageIntoOwnBucket() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(
            response(
                200,
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl())
        .startsWith("https://cdn.kurl.me/post-images/7/42/")
        .endsWith(".png");
    verify(objectStorage).putObject(anyString(), eq("image/png"), any(byte[].class));
  }

  @Test
  void importAcceptsOctetStreamContentType() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    // S3 서명 URL 은 저장 시 메타데이터에 따라 application/octet-stream 으로 내려주는 경우가 흔하다 —
    // 타입은 바이트 시그니처로 판별하므로 통과해야 한다.
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "application/octet-stream", PNG_BYTES));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl()).endsWith(".png");
    verify(objectStorage).putObject(anyString(), eq("image/png"), any(byte[].class));
  }

  @Test
  void importRehostsGif() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/gif", new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl()).endsWith(".gif");
    verify(objectStorage).putObject(anyString(), eq("image/gif"), any(byte[].class));
  }

  @Test
  void importRehostsWebp() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(
            response(
                200,
                "image/webp",
                new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl()).endsWith(".webp");
    verify(objectStorage).putObject(anyString(), eq("image/webp"), any(byte[].class));
  }

  @Test
  void importStoresSniffedTypeWhenDeclaredTypeLies() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    // Declares PNG but sends JPEG magic bytes — 저장 타입은 바이트 기준. HTML/SVG 폴리글랏은
    // 시그니처가 없어 여전히 거부된다 (importRejectsNonImageContentType).
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl()).endsWith(".jpg");
    verify(objectStorage).putObject(anyString(), eq("image/jpeg"), any(byte[].class));
  }

  @Test
  void importRejectsDisallowedUrl() {
    when(props.isConfigured()).thenReturn(true);
    assertThatThrownBy(() -> service.importFromUrl(7L, 42L, "ftp://example.com/a.png"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsNonImageContentType() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8)));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsOversizeBody() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(2L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[] {1, 2, 3, 4}));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsHttpError() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(404, "image/png", new byte[0]));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsNullUserId() {
    assertThatThrownBy(() -> service.importFromUrl(null, 42L, IMG_URL))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.INVALID_AVATAR);
  }

  @Test
  void importRejectsNullUrl() {
    when(props.isConfigured()).thenReturn(true);
    assertThatThrownBy(() -> service.importFromUrl(7L, 42L, null))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void importRejectsUnrecognizableBytes() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(new HttpFetcher.Response(200, Map.of(), new byte[] {1, 2}));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsEmptyBody() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[0]));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importWrapsFetchError() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenThrow(new RuntimeException("connection reset"));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importFollowsCrossHostRedirectRevalidatingEachHop() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    // 노션 이미지 프록시 패턴: www.notion.so/image/... → S3 서명 URL 로 cross-host 302.
    String s3Url = "https://prod-files-secure.s3.us-west-2.amazonaws.com/a.png?X-Amz-Signature=sig";
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(redirect(302, s3Url), response(200, "application/octet-stream", PNG_BYTES));

    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard
          .when(() -> PublicHttpUrlGuard.resolve(IMG_URL))
          .thenReturn(Optional.of(new Resolved(URI.create(IMG_URL), List.<InetAddress>of())));
      guard
          .when(() -> PublicHttpUrlGuard.resolve(s3Url))
          .thenReturn(Optional.of(new Resolved(URI.create(s3Url), List.<InetAddress>of())));

      PostImageService.CommitResult result = service.importFromUrl(7L, 42L, IMG_URL);

      assertThat(result.imageUrl()).endsWith(".png");
    }

    ArgumentCaptor<HttpFetcher.Request> requests =
        ArgumentCaptor.forClass(HttpFetcher.Request.class);
    verify(httpFetcher, times(2)).fetch(requests.capture());
    // 각 hop 은 자동 리다이렉트 없이 (guard 재검증을 위해) 직접 따라가야 한다.
    assertThat(requests.getAllValues()).allSatisfy(r -> assertThat(r.followRedirects()).isFalse());
    assertThat(requests.getAllValues().get(1).uri().toString()).isEqualTo(s3Url);
    verify(objectStorage).putObject(anyString(), eq("image/png"), any(byte[].class));
  }

  @Test
  void importRejectsRedirectToDisallowedHost() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    String metadataUrl = "http://169.254.169.254/latest/meta-data";
    when(httpFetcher.fetch(any(HttpFetcher.Request.class))).thenReturn(redirect(302, metadataUrl));

    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard
          .when(() -> PublicHttpUrlGuard.resolve(IMG_URL))
          .thenReturn(Optional.of(new Resolved(URI.create(IMG_URL), List.<InetAddress>of())));
      guard.when(() -> PublicHttpUrlGuard.resolve(metadataUrl)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.importFromUrl(7L, 42L, IMG_URL))
          .isInstanceOf(PostException.class)
          .extracting(e -> ((PostException) e).errorCode())
          .isEqualTo(PostErrorCode.PERMISSION_DENIED);
    }
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsTooManyRedirects() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class))).thenReturn(redirect(302, IMG_URL));

    whenImport().runExpectingThrow();
    verify(httpFetcher, times(6)).fetch(any(HttpFetcher.Request.class));
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsRedirectMissingLocation() {
    when(props.isConfigured()).thenReturn(true);
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(new HttpFetcher.Response(302, Map.of(), new byte[0]));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }
}
