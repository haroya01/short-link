package com.example.short_link.common.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3ObjectStorageTest {

  private final S3Client s3 = mock(S3Client.class);
  private final S3Presigner presigner = mock(S3Presigner.class);

  private S3ObjectStorage storage(boolean configured) {
    S3StorageProperties props =
        configured
            ? new S3StorageProperties("bucket-x", "ap-northeast-2", "https://cdn")
            : new S3StorageProperties("", "", "");
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

  @Test
  void getObjectReturnsTheBytesOrNothingWhenTheKeyIsMissing() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(
            ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1, 2}))
        .thenThrow(NoSuchKeyException.builder().message("gone").build())
        .thenThrow(S3Exception.builder().message("boom").build());

    assertThat(storage(true).getObject("note-images/1/a.jpg")).contains(new byte[] {1, 2});
    assertThat(storage(true).getObject("note-images/1/b.jpg")).isEmpty();
    assertThatThrownBy(() -> storage(true).getObject("note-images/1/c.jpg"))
        .isInstanceOf(ObjectStorageException.class);
    ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3, Mockito.times(3)).getObjectAsBytes(captor.capture());
    assertThat(captor.getAllValues().getFirst().bucket()).isEqualTo("bucket-x");
    assertThat(captor.getAllValues().getFirst().key()).isEqualTo("note-images/1/a.jpg");
  }

  @Test
  void deletingAnImageDeletesItsVariantsToo() {
    storage(true).deleteWithVariants("note-images/1/a.jpeg");
    storage(true).deleteWithVariants("avatars/1/a.jpg");

    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3, Mockito.times(4)).deleteObject(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(DeleteObjectRequest::key)
        .containsExactly(
            "note-images/1/a.jpeg",
            "note-images/1/a.w1080.jpg",
            "note-images/1/a.w480.jpg",
            "avatars/1/a.jpg");
  }

  @Test
  void deleteFolderDeletesEveryPageOfTheListing() {
    when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("avatars/7/a.png").build())
                .isTruncated(true)
                .nextContinuationToken("next")
                .build())
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("avatars/7/b.png").build())
                .isTruncated(false)
                .build());
    when(s3.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());

    storage(true).deleteFolder("avatars/7/");

    ArgumentCaptor<ListObjectsV2Request> listed =
        ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3, Mockito.times(2)).listObjectsV2(listed.capture());
    assertThat(listed.getAllValues())
        .extracting(ListObjectsV2Request::prefix, ListObjectsV2Request::continuationToken)
        .containsExactly(tuple("avatars/7/", null), tuple("avatars/7/", "next"));
    ArgumentCaptor<DeleteObjectsRequest> deleted =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(s3, Mockito.times(2)).deleteObjects(deleted.capture());
    assertThat(deleted.getAllValues())
        .flatExtracting(request -> request.delete().objects())
        .extracting(ObjectIdentifier::key)
        .containsExactly("avatars/7/a.png", "avatars/7/b.png");
  }

  @Test
  void deleteFolderRefusesAnythingThatIsNotAFolder() {
    for (String notAFolder : new String[] {null, "", "avatars/7"}) {
      assertThatThrownBy(() -> storage(true).deleteFolder(notAFolder))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verify(s3, Mockito.never()).listObjectsV2(any(ListObjectsV2Request.class));
  }

  @Test
  void deleteFolderFailsWhenStorageKeepsSomeObjects() {
    when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("avatars/7/a.png").build())
                .build());
    when(s3.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(
            DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("avatars/7/a.png").code("AccessDenied").build())
                .build());

    assertThatThrownBy(() -> storage(true).deleteFolder("avatars/7/"))
        .isInstanceOf(ObjectStorageException.class)
        .hasMessageContaining("AccessDenied");
  }
}
