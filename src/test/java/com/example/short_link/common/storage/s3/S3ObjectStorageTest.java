package com.example.short_link.common.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorageException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3ObjectStorageTest {

  private final S3Client s3 = mock(S3Client.class);
  private final S3Presigner presigner = mock(S3Presigner.class);

  private S3ObjectStorage storage(boolean configured) {
    AvatarProperties props =
        configured
            ? new AvatarProperties("bucket-x", "ap-northeast-2", "https://cdn", 0, 0)
            : new AvatarProperties("", "", "", 0, 0);
    return new S3ObjectStorage(s3, presigner, props);
  }

  @Test
  void isConfiguredReflectsProperties() {
    assertThat(storage(true).isConfigured()).isTrue();
    assertThat(storage(false).isConfigured()).isFalse();
  }

  @Test
  void presignPutReturnsSignedUrl() throws Exception {
    PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
    when(signed.url()).thenReturn(URI.create("https://signed.example/x?sig=1").toURL());
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);

    String url = storage(true).presignPut("avatars/u-1.png", "image/png", Duration.ofMinutes(5));

    assertThat(url).isEqualTo("https://signed.example/x?sig=1");
  }

  @Test
  void presignPutWrapsSdkExceptionAsObjectStorage() {
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenThrow(S3Exception.builder().message("boom").build());

    assertThatThrownBy(
            () -> storage(true).presignPut("avatars/u-2.png", "image/png", Duration.ofMinutes(5)))
        .isInstanceOf(ObjectStorageException.class);
  }

  @Test
  void objectSizeReturnsContentLength() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(2048L).build();
    when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(head);

    Optional<Long> size = storage(true).objectSize("avatars/u-3.png");

    assertThat(size).contains(2048L);
  }

  @Test
  void objectSizeReturnsEmptyWhenSdkFails() {
    when(s3.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().message("404").build());

    assertThat(storage(true).objectSize("avatars/missing.png")).isEmpty();
  }

  @Test
  void deleteInvokesSdkClient() {
    storage(true).delete("avatars/u-4.png");

    verify(s3).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void deleteWrapsSdkExceptionAsObjectStorage() {
    Mockito.doThrow(S3Exception.builder().message("denied").build())
        .when(s3)
        .deleteObject(any(DeleteObjectRequest.class));

    assertThatThrownBy(() -> storage(true).delete("avatars/u-5.png"))
        .isInstanceOf(ObjectStorageException.class);
  }

  @Test
  void putObjectInvokesSdkClient() {
    storage(true).putObject("post-images/7/42/x.png", "image/png", new byte[] {1, 2, 3});

    verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void putObjectWrapsSdkExceptionAsObjectStorage() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().message("boom").build());

    assertThatThrownBy(
            () -> storage(true).putObject("post-images/7/42/x.png", "image/png", new byte[] {1}))
        .isInstanceOf(ObjectStorageException.class);
  }

  @Test
  void putObjectTagsImmutableCacheControl() {
    storage(true).putObject("post-images/7/42/x.png", "image/png", new byte[] {1, 2, 3});

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().cacheControl()).isEqualTo(S3ObjectStorage.IMMUTABLE_CACHE_CONTROL);
  }

  @Test
  void applyImmutableCacheControlCopiesInPlacePreservingContentType() {
    when(s3.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentType("image/webp").build());

    storage(true).applyImmutableCacheControl("avatars/1/x.webp");

    ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
    verify(s3).copyObject(captor.capture());
    CopyObjectRequest copy = captor.getValue();
    assertThat(copy.sourceKey()).isEqualTo("avatars/1/x.webp");
    assertThat(copy.destinationKey()).isEqualTo("avatars/1/x.webp");
    assertThat(copy.metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
    assertThat(copy.contentType()).isEqualTo("image/webp");
    assertThat(copy.cacheControl()).isEqualTo(S3ObjectStorage.IMMUTABLE_CACHE_CONTROL);
  }

  @Test
  void applyImmutableCacheControlSwallowsSdkFailure() {
    when(s3.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().message("boom").build());

    storage(true).applyImmutableCacheControl("avatars/1/x.webp");

    verify(s3, Mockito.never()).copyObject(any(CopyObjectRequest.class));
  }
}
