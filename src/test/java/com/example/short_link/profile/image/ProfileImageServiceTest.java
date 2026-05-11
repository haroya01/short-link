package com.example.short_link.profile.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.avatar.AvatarProperties;
import com.example.short_link.user.application.avatar.AvatarUnavailableException;
import com.example.short_link.user.application.avatar.InvalidAvatarException;
import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

class ProfileImageServiceTest {

  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private AvatarProperties props;
  private S3Presigner presigner;
  private S3Client s3Client;
  private ProfileImageService service;

  @BeforeEach
  void setUp() throws Exception {
    props =
        new AvatarProperties("test-bucket", "ap-northeast-2", "https://cdn.test", 300, MAX_BYTES);
    presigner = mock(S3Presigner.class);
    s3Client = mock(S3Client.class);
    service = new ProfileImageService(props, presigner, s3Client);

    PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
    URL signedUrl = URI.create("https://test-bucket.s3.amazonaws.com/signed").toURL();
    when(signed.url()).thenReturn(signedUrl);
    when(presigner.presignPutObject(
            any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
        .thenReturn(signed);
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
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, ""))
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, null))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void presignRequiresUserId() {
    assertThatThrownBy(() -> service.presignUpload(null, "image/jpeg"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void presignFailsWhenBucketUnconfigured() {
    AvatarProperties unconfigured = new AvatarProperties("", "", null, 300, MAX_BYTES);
    ProfileImageService unconfiguredService =
        new ProfileImageService(unconfigured, presigner, s3Client);
    assertThatThrownBy(() -> unconfiguredService.presignUpload(1L, "image/jpeg"))
        .isInstanceOf(AvatarUnavailableException.class);
  }

  @Test
  void commitRejectsKeyOwnedByDifferentUser() {
    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/999/xyz.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("not owned");
  }

  @Test
  void commitRejectsKeyWithoutPrefix() {
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/abc.jpg"))
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, ""))
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, null))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitRejectsMissingObject() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("nope").build());
    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/1/abc.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void commitRejectsOversizeAndDeletesObject() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(MAX_BYTES + 1).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);

    assertThatThrownBy(() -> service.commitUpload(1L, "profile-images/1/big.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("exceeds maxBytes");

    verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void commitReturnsPublicUrlOnSuccess() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(1024L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);

    ProfileImageService.CommitResult out = service.commitUpload(1L, "profile-images/1/ok.jpg");
    assertThat(out.imageUrl()).isEqualTo("https://cdn.test/profile-images/1/ok.jpg");
    assertThat(out.key()).isEqualTo("profile-images/1/ok.jpg");
    verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void commitRequiresUserId() {
    assertThatThrownBy(() -> service.commitUpload(null, "profile-images/1/a.jpg"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitFallsBackToVirtualHostUrlWhenNoCdn() {
    AvatarProperties noBaseUrl =
        new AvatarProperties("test-bucket", "ap-northeast-2", null, 300, MAX_BYTES);
    ProfileImageService noCdnService = new ProfileImageService(noBaseUrl, presigner, s3Client);
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(1024L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);

    ProfileImageService.CommitResult out = noCdnService.commitUpload(1L, "profile-images/1/x.jpg");
    assertThat(out.imageUrl())
        .isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/profile-images/1/x.jpg");
  }

  @Test
  void commitToleratesObjectsWithNullContentLength() {
    // Some S3-compatible providers (or weird HEAD responses) may not return content-length.
    // The service should still allow commit rather than 500 — frontend resize gave us best-effort.
    HeadObjectResponse head = HeadObjectResponse.builder().build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    ProfileImageService.CommitResult out =
        service.commitUpload(1L, "profile-images/1/unknown-size.jpg");
    assertThat(out.imageUrl()).contains("/profile-images/1/unknown-size.jpg");
  }
}
