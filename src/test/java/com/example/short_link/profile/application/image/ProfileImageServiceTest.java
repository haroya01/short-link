package com.example.short_link.profile.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileImageServiceTest {

  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private AvatarProperties props;
  private ObjectStorage objectStorage;
  private ProfileImageService service;

  @BeforeEach
  void setUp() {
    props =
        new AvatarProperties("test-bucket", "ap-northeast-2", "https://cdn.test", 300, MAX_BYTES);
    objectStorage = mock(ObjectStorage.class);
    service = new ProfileImageService(props, objectStorage);
    when(objectStorage.presignPut(any(), any(), any(Duration.class))).thenReturn("https://signed");
  }

  @Test
  void presignBuildsKeyUnderUserPrefix() {
    ProfileImageService.PresignResult out = service.presignUpload(42L, "image/jpeg");
    assertThat(out.key()).startsWith("profile-images/42/");
    assertThat(out.key()).endsWith(".jpg");
    assertThat(out.contentType()).isEqualTo("image/jpeg");
    assertThat(out.maxBytes()).isEqualTo(MAX_BYTES);
    assertThat(out.publicUrl()).startsWith("https://cdn.test/profile-images/42/");
  }

  @Test
  void presignNormalizesContentTypeCasingAndWhitespace() {
    ProfileImageService.PresignResult out = service.presignUpload(7L, " IMAGE/PNG ");
    assertThat(out.contentType()).isEqualTo("image/png");
    assertThat(out.key()).endsWith(".png");
  }

  @Test
  void presignRejectsUnsupportedContentType() {
    assertThatThrownBy(() -> service.presignUpload(1L, "image/gif"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, "")).isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, null)).isInstanceOf(UserException.class);
  }

  @Test
  void presignRequiresUserId() {
    assertThatThrownBy(() -> service.presignUpload(null, "image/jpeg"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void presignFailsWhenBucketUnconfigured() {
    AvatarProperties unconfigured = new AvatarProperties("", "", null, 300, MAX_BYTES);
    ProfileImageService unconfiguredService = new ProfileImageService(unconfigured, objectStorage);
    assertThatThrownBy(() -> unconfiguredService.presignUpload(1L, "image/jpeg"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void commitRejectsKeyOwnedByDifferentUser() {
    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/999/xyz.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("not owned");
  }

  @Test
  void commitRejectsKeyWithoutPrefix() {
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/abc.jpg"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, "")).isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, null)).isInstanceOf(UserException.class);
  }

  @Test
  void commitRejectsMissingObject() {
    when(objectStorage.objectSize("profile-images/1/abc.jpg")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/1/abc.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void commitRejectsOversizeAndDeletesObject() {
    when(objectStorage.objectSize("profile-images/1/big.jpg"))
        .thenReturn(Optional.of(MAX_BYTES + 1));
    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/1/big.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("exceeds maxBytes");
    verify(objectStorage, times(1)).delete("profile-images/1/big.jpg");
  }

  @Test
  void commitReturnsPublicUrlOnSuccess() {
    when(objectStorage.objectSize("profile-images/1/ok.jpg")).thenReturn(Optional.of(1024L));
    ProfileImageService.CommitResult out = service.commitUpload(1L, "profile-images/1/ok.jpg");
    assertThat(out.imageUrl()).isEqualTo("https://cdn.test/profile-images/1/ok.jpg");
    assertThat(out.key()).isEqualTo("profile-images/1/ok.jpg");
    verify(objectStorage, never()).delete(eq("profile-images/1/ok.jpg"));
  }

  @Test
  void commitRequiresUserId() {
    assertThatThrownBy(() -> service.commitUpload(null, "profile-images/1/a.jpg"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void commitFallsBackToVirtualHostUrlWhenNoCdn() {
    AvatarProperties noBaseUrl =
        new AvatarProperties("test-bucket", "ap-northeast-2", null, 300, MAX_BYTES);
    ProfileImageService noCdnService = new ProfileImageService(noBaseUrl, objectStorage);
    when(objectStorage.objectSize("profile-images/1/x.jpg")).thenReturn(Optional.of(1024L));
    ProfileImageService.CommitResult out = noCdnService.commitUpload(1L, "profile-images/1/x.jpg");
    assertThat(out.imageUrl())
        .isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/profile-images/1/x.jpg");
  }
}
