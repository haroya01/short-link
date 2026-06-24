package com.example.short_link.post.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.domain.PostEntity;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

  private static final String IMG_URL = "https://cdn.notion.so/secure/abc.png";

  @Mock private AvatarProperties props;
  @Mock private ObjectStorage objectStorage;
  @Mock private PostOwnership postOwnership;
  @Mock private HttpFetcher httpFetcher;

  private PostImageService service;

  @BeforeEach
  void setUp() {
    service = new PostImageService(props, objectStorage, postOwnership, httpFetcher);
  }

  private PostEntity ownedPost() {
    return new PostEntity(7L, "p", "P", "ko");
  }

  private static HttpFetcher.Response response(int status, String contentType, byte[] body) {
    return new HttpFetcher.Response(status, Map.of("Content-Type", List.of(contentType)), body);
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
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
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
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());

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
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
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
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());

    assertThatThrownBy(() -> service.commitUpload(7L, 42L, "post-images/9/99/uuid.png"))
        .isInstanceOf(PostException.class);
  }

  @Test
  void commitRejectsOversize() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(objectStorage.objectSize("post-images/7/42/uuid.png"))
        .thenReturn(Optional.of(10_000_000L));
    when(props.maxBytes()).thenReturn(5_000_000L);

    assertThatThrownBy(() -> service.commitUpload(7L, 42L, "post-images/7/42/uuid.png"))
        .isInstanceOf(PostException.class);
  }

  @Test
  void importRehostsExternalImageIntoOwnBucket() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[] {1, 2, 3, 4}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl())
        .startsWith("https://cdn.kurl.me/post-images/7/42/")
        .endsWith(".png");
    verify(objectStorage).putObject(anyString(), eq("image/png"), any(byte[].class));
  }

  @Test
  void importStripsCharsetFromContentType() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(props.publicBaseUrl()).thenReturn("https://cdn.kurl.me");
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/jpeg; charset=binary", new byte[] {1, 2}));

    PostImageService.CommitResult result = whenImport().run();

    assertThat(result.imageUrl()).endsWith(".jpg");
    verify(objectStorage).putObject(anyString(), eq("image/jpeg"), any(byte[].class));
  }

  @Test
  void importRejectsDisallowedUrl() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());

    assertThatThrownBy(() -> service.importFromUrl(7L, 42L, "ftp://example.com/a.png"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsNonImageContentType() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8)));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsOversizeBody() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(2L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[] {1, 2, 3, 4}));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsHttpError() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
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
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());

    assertThatThrownBy(() -> service.importFromUrl(7L, 42L, null))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void importRejectsMissingContentType() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(new HttpFetcher.Response(200, Map.of(), new byte[] {1, 2}));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importRejectsEmptyBody() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(response(200, "image/png", new byte[0]));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }

  @Test
  void importWrapsFetchError() {
    when(props.isConfigured()).thenReturn(true);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(ownedPost());
    when(props.maxBytes()).thenReturn(5_000_000L);
    when(httpFetcher.fetch(any(HttpFetcher.Request.class)))
        .thenThrow(new RuntimeException("connection reset"));

    whenImport().runExpectingThrow();
    verify(objectStorage, never()).putObject(anyString(), anyString(), any(byte[].class));
  }
}
