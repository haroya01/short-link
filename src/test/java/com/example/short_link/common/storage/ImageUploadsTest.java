package com.example.short_link.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.s3.S3StorageProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageUploadsTest {

  private static final ImageUploadPolicy POLICY = new ImageUploadPolicy(120, 1000);

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ImageUploads uploads =
      new ImageUploads(
          POLICY,
          storage,
          new ObjectStoragePublicUrls(
              new S3StorageProperties("bucket", "ap-northeast-2", "https://cdn.kurl.me")));
  private final ImageRejections rejections =
      new ImageRejections(
          () -> new IllegalStateException("unconfigured"),
          allowed -> new IllegalArgumentException("type " + allowed.size()),
          () -> new IllegalArgumentException("not owned"),
          () -> new IllegalArgumentException("missing"),
          (size, max) -> new IllegalArgumentException("oversized " + size + "/" + max));

  @Test
  void anUnconfiguredStorageIsRejectedTheCallersWay() {
    when(storage.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> uploads.requireConfigured(rejections)).hasMessage("unconfigured");
  }

  @Test
  void aPresignedKeyLivesUnderTheOwnedPrefixWithTheTypesExtension() {
    when(storage.presignPut(any(), eq("image/png"), eq(Duration.ofSeconds(120))))
        .thenReturn("https://signed");

    PresignedUpload upload =
        uploads.presign("avatars/7/", " Image/PNG ", ImageUploads.STILL, rejections);

    assertThat(upload.key()).startsWith("avatars/7/").endsWith(".png");
    assertThat(upload.contentType()).isEqualTo("image/png");
    assertThat(upload.publicUrl()).isEqualTo("https://cdn.kurl.me/" + upload.key());
    assertThat(upload.maxBytes()).isEqualTo(1000);
    assertThat(upload.expiresIn()).isEqualTo(120);
  }

  @Test
  void onlyTheAllowedTypesArePresigned() {
    assertThatThrownBy(
            () -> uploads.presign("avatars/7/", "image/gif", ImageUploads.STILL, rejections))
        .hasMessage("type 3");
    when(storage.presignPut(any(), eq("image/gif"), any())).thenReturn("https://signed");
    assertThat(
            uploads.presign("posts/7/1/", "image/gif", ImageUploads.STILL_OR_GIF, rejections).key())
        .endsWith(".gif");
  }

  @Test
  void aCommitChecksOwnershipThenPresenceThenSize() {
    assertThatThrownBy(() -> uploads.commit("avatars/7/", "avatars/8/x.png", rejections))
        .hasMessage("not owned");
    assertThatThrownBy(() -> uploads.commit("avatars/7/", " ", rejections)).hasMessage("not owned");
    when(storage.objectSize("avatars/7/x.png")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> uploads.commit("avatars/7/", "avatars/7/x.png", rejections))
        .hasMessage("missing");
  }

  @Test
  void anOversizedUploadIsDeletedAndRejected() {
    when(storage.objectSize("avatars/7/x.png")).thenReturn(Optional.of(1001L));
    doThrow(new ObjectStorageException("gone", null)).when(storage).delete("avatars/7/x.png");

    assertThatThrownBy(() -> uploads.commit("avatars/7/", "avatars/7/x.png", rejections))
        .hasMessage("oversized 1001/1000");
    verify(storage).delete("avatars/7/x.png");
    verify(storage, never()).applyImmutableCacheControl(any());
  }

  @Test
  void anOversizedPostImageTakesItsVariantsWithIt() {
    when(storage.objectSize("posts/7/1/x.png")).thenReturn(Optional.of(1001L));

    assertThatThrownBy(
            () -> uploads.commitWithVariants("posts/7/1/", "posts/7/1/x.png", rejections))
        .hasMessage("oversized 1001/1000");
    verify(storage).deleteWithVariants("posts/7/1/x.png");
    verify(storage, never()).delete(any());
  }

  @Test
  void aCommittedUploadIsCachedForeverAndServedFromItsPublicUrl() {
    when(storage.objectSize("avatars/7/x.png")).thenReturn(Optional.of(1000L));

    assertThat(uploads.commit("avatars/7/", "avatars/7/x.png", rejections))
        .isEqualTo("https://cdn.kurl.me/avatars/7/x.png");
    verify(storage).applyImmutableCacheControl("avatars/7/x.png");
  }
}
