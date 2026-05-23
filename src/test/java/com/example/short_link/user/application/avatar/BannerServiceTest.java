package com.example.short_link.user.application.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ObjectStorage objectStorage;

  private AvatarProperties props;
  private BannerService service;

  @BeforeEach
  void setUp() {
    props = new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com", 300, 1024);
    service = new BannerService(userRepository, props, objectStorage);
  }

  @Test
  void presignFailsWhenNotConfigured() {
    AvatarProperties noBucket = new AvatarProperties("", "", null, 300, 1024);
    BannerService svc = new BannerService(userRepository, noBucket, objectStorage);
    assertThatThrownBy(() -> svc.presignUpload(1L, "image/jpeg"))
        .isInstanceOf(AvatarUnavailableException.class);
  }

  @Test
  void presignRejectsUnsupportedContentType() {
    assertThatThrownBy(() -> service.presignUpload(1L, "image/svg+xml"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void presignReturnsPresignedUrl() {
    when(objectStorage.presignPut(any(), eq("image/png"), any(Duration.class)))
        .thenReturn("https://s3/put");
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
    when(objectStorage.objectSize("banners/1/x.png")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("upload not found");
  }

  @Test
  void commitDeletesOversizedUpload() {
    when(objectStorage.objectSize("banners/1/x.png")).thenReturn(Optional.of(9999L));
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("exceeds maxBytes");
    verify(objectStorage).delete("banners/1/x.png");
  }

  @Test
  void commitSwallowsOversizeDeleteFailure() {
    when(objectStorage.objectSize("banners/1/x.png")).thenReturn(Optional.of(9999L));
    org.mockito.Mockito.doThrow(new ObjectStorageException("boom", new RuntimeException()))
        .when(objectStorage)
        .delete("banners/1/x.png");
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitMissingUserThrows() {
    when(objectStorage.objectSize("banners/1/x.png")).thenReturn(Optional.of(100L));
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "banners/1/x.png"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void commitSetsBannerAndDeletesPrevious() {
    when(objectStorage.objectSize("banners/1/new.png")).thenReturn(Optional.of(100L));
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateBanner("https://old", "banners/1/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    BannerService.CommitResult r = service.commitUpload(1L, "banners/1/new.png");
    assertThat(r.bannerUrl()).isEqualTo("https://cdn.example.com/banners/1/new.png");
    assertThat(user.getBannerKey()).isEqualTo("banners/1/new.png");
    verify(objectStorage).delete("banners/1/old.png");
  }

  @Test
  void commitSwallowsPreviousDeleteFailure() {
    when(objectStorage.objectSize("banners/1/new.png")).thenReturn(Optional.of(100L));
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateBanner("https://old", "banners/1/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    org.mockito.Mockito.doThrow(new ObjectStorageException("boom", new RuntimeException()))
        .when(objectStorage)
        .delete("banners/1/old.png");
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
    verify(objectStorage).delete("banners/1/old.png");
  }

  @Test
  void clearBannerWithoutPreviousSkipsDelete() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    service.clearBanner(1L);
    verify(objectStorage, never()).delete(any());
  }

  @Test
  void clearBannerMissingUserThrows() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.clearBanner(1L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void publicUrlFallsBackToStandardS3WhenCdnBlank() {
    AvatarProperties noCdn = new AvatarProperties("bucket", "ap-northeast-2", null, 300, 1024);
    BannerService svc = new BannerService(userRepository, noCdn, objectStorage);
    when(objectStorage.presignPut(any(), eq("image/png"), any(Duration.class)))
        .thenReturn("https://s3/put");
    BannerService.PresignResult r = svc.presignUpload(1L, "image/png");
    assertThat(r.publicUrl()).startsWith("https://bucket.s3.ap-northeast-2.amazonaws.com/");
  }
}
