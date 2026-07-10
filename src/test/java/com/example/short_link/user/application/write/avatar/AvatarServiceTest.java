package com.example.short_link.user.application.write.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ObjectStorage objectStorage;

  private AvatarProperties props;
  private AvatarService service;

  @BeforeEach
  void setUp() {
    props = new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com", 300, 1024);
    service =
        new AvatarService(
            userRepository, props, objectStorage, mock(ProfileCacheInvalidator.class));
  }

  @Test
  void presignFailsWhenNotConfigured() {
    AvatarProperties noBucket = new AvatarProperties("", "", null, 300, 1024);
    AvatarService svc =
        new AvatarService(
            userRepository, noBucket, objectStorage, mock(ProfileCacheInvalidator.class));
    assertThatThrownBy(() -> svc.presignUpload(1L, "image/jpeg")).isInstanceOf(UserException.class);
  }

  @Test
  void presignRejectsUnsupportedContentType() {
    assertThatThrownBy(() -> service.presignUpload(1L, "image/svg+xml"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, null)).isInstanceOf(UserException.class);
  }

  @Test
  void presignReturnsPresignedUrlForJpeg() {
    when(objectStorage.presignPut(any(), eq("image/jpeg"), any(Duration.class)))
        .thenReturn("https://s3/put");

    AvatarService.PresignResult r = service.presignUpload(42L, "  IMAGE/JPEG ");
    assertThat(r.uploadUrl()).isEqualTo("https://s3/put");
    assertThat(r.key()).startsWith("avatars/42/").endsWith(".jpg");
    assertThat(r.publicUrl()).startsWith("https://cdn.example.com/avatars/42/").endsWith(".jpg");
    assertThat(r.contentType()).isEqualTo("image/jpeg");
    assertThat(r.maxBytes()).isEqualTo(1024);
    assertThat(r.expiresIn()).isEqualTo(300);
  }

  @Test
  void commitRejectsKeyNotOwnedByUser() {
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/2/x.jpg"))
        .isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, "")).isInstanceOf(UserException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, null)).isInstanceOf(UserException.class);
  }

  @Test
  void commitFailsWhenObjectMissing() {
    when(objectStorage.objectSize("avatars/1/x.jpg")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("upload not found");
  }

  @Test
  void commitDeletesOversizedUploadAndThrows() {
    when(objectStorage.objectSize("avatars/1/x.jpg")).thenReturn(Optional.of(9999L));
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("exceeds maxBytes");
    verify(objectStorage).delete("avatars/1/x.jpg");
  }

  @Test
  void commitSwallowsDeleteFailureOnOversize() {
    when(objectStorage.objectSize("avatars/1/x.jpg")).thenReturn(Optional.of(9999L));
    Mockito.doThrow(new ObjectStorageException("s3 boom", new RuntimeException()))
        .when(objectStorage)
        .delete("avatars/1/x.jpg");
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(UserException.class)
        .hasMessageContaining("exceeds maxBytes");
  }

  @Test
  void commitThrowsWhenUserMissing() {
    when(objectStorage.objectSize("avatars/1/x.jpg")).thenReturn(Optional.of(100L));
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void commitSetsAvatarAndDeletesPreviousKey() {
    when(objectStorage.objectSize("avatars/1/new.jpg")).thenReturn(Optional.of(100L));
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateAvatar("https://old", "avatars/1/old.jpg");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    AvatarService.CommitResult r = service.commitUpload(1L, "avatars/1/new.jpg");
    assertThat(r.avatarUrl()).isEqualTo("https://cdn.example.com/avatars/1/new.jpg");
    assertThat(user.getAvatarKey()).isEqualTo("avatars/1/new.jpg");
    verify(objectStorage).delete("avatars/1/old.jpg");
    verify(objectStorage, never()).delete("avatars/1/new.jpg");
    verify(objectStorage).applyImmutableCacheControl("avatars/1/new.jpg");
  }

  @Test
  void commitSwallowsPreviousDeleteFailure() {
    when(objectStorage.objectSize("avatars/1/new.jpg")).thenReturn(Optional.of(100L));
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateAvatar("https://old", "avatars/1/old.jpg");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    Mockito.doThrow(new ObjectStorageException("s3 boom", new RuntimeException()))
        .when(objectStorage)
        .delete("avatars/1/old.jpg");
    AvatarService.CommitResult r = service.commitUpload(1L, "avatars/1/new.jpg");
    assertThat(r.avatarUrl()).contains("/avatars/1/new.jpg");
  }

  @Test
  void clearAvatarNullsFieldsAndDeletesPrevious() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateAvatar("https://old", "avatars/1/old.jpg");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    service.clearAvatar(1L);
    assertThat(user.getAvatarUrl()).isNull();
    assertThat(user.getAvatarKey()).isNull();
    verify(objectStorage).delete("avatars/1/old.jpg");
  }

  @Test
  void clearAvatarOnUserWithoutPreviousSkipsDelete() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    service.clearAvatar(1L);
    verify(objectStorage, never()).delete(any());
  }

  @Test
  void clearAvatarThrowsWhenUserMissing() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.clearAvatar(1L)).isInstanceOf(UserException.class);
  }

  @Test
  void publicUrlFallsBackToStandardS3HostWhenCdnBlank() {
    AvatarProperties noCdn = new AvatarProperties("bucket", "ap-northeast-2", null, 300, 1024);
    AvatarService svc =
        new AvatarService(
            userRepository, noCdn, objectStorage, mock(ProfileCacheInvalidator.class));
    when(objectStorage.presignPut(any(), eq("image/png"), any(Duration.class)))
        .thenReturn("https://s3/put");
    AvatarService.PresignResult r = svc.presignUpload(1L, "image/png");
    assertThat(r.publicUrl()).startsWith("https://bucket.s3.ap-northeast-2.amazonaws.com/");
  }

  @Test
  void publicUrlStripsTrailingSlashOnBase() {
    AvatarProperties slashed =
        new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com/", 300, 1024);
    AvatarService svc =
        new AvatarService(
            userRepository, slashed, objectStorage, mock(ProfileCacheInvalidator.class));
    when(objectStorage.presignPut(any(), eq("image/webp"), any(Duration.class)))
        .thenReturn("https://s3/put");
    AvatarService.PresignResult r = svc.presignUpload(1L, "image/webp");
    assertThat(r.publicUrl()).startsWith("https://cdn.example.com/avatars/1/").endsWith(".webp");
  }
}
