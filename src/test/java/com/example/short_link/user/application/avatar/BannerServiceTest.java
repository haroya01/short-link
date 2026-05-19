package com.example.short_link.user.application.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private S3Presigner presigner;
  @Mock private S3Client s3Client;

  private AvatarProperties props;
  private BannerService service;

  @BeforeEach
  void setUp() {
    props = new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com", 300, 1024);
    service = new BannerService(userRepository, props, presigner, s3Client);
  }

  @Test
  void presignFailsWhenNotConfigured() {
    AvatarProperties noBucket = new AvatarProperties("", "", null, 300, 1024);
    BannerService svc = new BannerService(userRepository, noBucket, presigner, s3Client);
    assertThatThrownBy(() -> svc.presignUpload(1L, "image/jpeg"))
        .isInstanceOf(AvatarUnavailableException.class);
  }

  @Test
  void presignRejectsUnsupportedContentType() {
    assertThatThrownBy(() -> service.presignUpload(1L, "image/svg+xml"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void presignReturnsPresignedUrl() throws Exception {
    PresignedPutObjectRequest signed = mockSigned("https://s3/put");
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
    BannerService.PresignResult r = service.presignUpload(7L, "image/png");
    assertThat(r.uploadUrl()).isEqualTo("https://s3/put");
    assertThat(r.key()).startsWith("banners/7/").endsWith(".png");
    assertThat(r.publicUrl()).startsWith("https://cdn.example.com/banners/7/").endsWith(".png");
  }

  @Test
  void commitRejectsForeignKey() {
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/2/x.png"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitFailsWhenObjectMissing() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(new RuntimeException("missing"));
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("upload not found");
  }

  @Test
  void commitDeletesOversizedUpload() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(9999L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("exceeds maxBytes");
    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void commitSwallowsOversizeDeleteFailure() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(9999L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(new RuntimeException("boom"));
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitMissingUserThrows() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void commitSetsBannerAndDeletesPrevious() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateBanner("https://old", "banners/1/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    BannerService.CommitResult r = service.commitUpload(1L, "banners/1/new.png");
    assertThat(r.bannerUrl()).isEqualTo("https://cdn.example.com/banners/1/new.png");
    assertThat(user.getBannerKey()).isEqualTo("banners/1/new.png");
  }

  @Test
  void commitSwallowsPreviousDeleteFailure() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateBanner("https://old", "banners/1/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(new RuntimeException("boom"));
    BannerService.CommitResult r = service.commitUpload(1L, "banners/1/new.png");
    assertThat(r.bannerUrl()).contains("/banners/1/new.png");
  }

  @Test
  void clearBannerDeletesPrevious() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateBanner("https://old", "banners/1/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    service.clearBanner(1L);
    assertThat(user.getBannerUrl()).isNull();
    assertThat(user.getBannerKey()).isNull();
    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void clearBannerWithoutPreviousSkipsDelete() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    service.clearBanner(1L);
    verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void clearBannerMissingUserThrows() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.clearBanner(1L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void publicUrlFallsBackToStandardS3WhenCdnBlank() throws Exception {
    AvatarProperties noCdn = new AvatarProperties("bucket", "ap-northeast-2", null, 300, 1024);
    BannerService svc = new BannerService(userRepository, noCdn, presigner, s3Client);
    PresignedPutObjectRequest signed = mockSigned("https://s3/put");
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
    BannerService.PresignResult r = svc.presignUpload(1L, "image/png");
    assertThat(r.publicUrl()).startsWith("https://bucket.s3.ap-northeast-2.amazonaws.com/");
  }

  private static PresignedPutObjectRequest mockSigned(String url) throws Exception {
    PresignedPutObjectRequest signed = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
    when(signed.url()).thenReturn(URI.create(url).toURL());
    return signed;
  }
}
