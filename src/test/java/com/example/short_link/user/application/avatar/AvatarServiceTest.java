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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private S3Presigner presigner;
  @Mock private S3Client s3Client;

  private AvatarProperties props;
  private AvatarService service;

  @BeforeEach
  void setUp() {
    props = new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com", 300, 1024);
    service = new AvatarService(userRepository, props, presigner, s3Client);
  }

  @Test
  void presignFailsWhenNotConfigured() {
    AvatarProperties noBucket = new AvatarProperties("", "", null, 300, 1024);
    AvatarService svc = new AvatarService(userRepository, noBucket, presigner, s3Client);
    assertThatThrownBy(() -> svc.presignUpload(1L, "image/jpeg"))
        .isInstanceOf(AvatarUnavailableException.class);
  }

  @Test
  void presignRejectsUnsupportedContentType() {
    assertThatThrownBy(() -> service.presignUpload(1L, "image/svg+xml"))
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.presignUpload(1L, null))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void presignReturnsPresignedUrlForJpeg() throws Exception {
    PresignedPutObjectRequest signed = mockSigned("https://s3/put");
    when(presigner.presignPutObject(
            any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
        .thenReturn(signed);

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
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, ""))
        .isInstanceOf(InvalidAvatarException.class);
    assertThatThrownBy(() -> service.commitUpload(1L, null))
        .isInstanceOf(InvalidAvatarException.class);
  }

  @Test
  void commitFailsWhenHeadObjectMissing() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(new RuntimeException("missing"));
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("upload not found");
  }

  @Test
  void commitDeletesOversizedUploadAndThrows() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(9999L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("exceeds maxBytes");
    verify(s3Client)
        .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
  }

  @Test
  void commitSwallowsDeleteFailureOnOversize() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(9999L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    when(s3Client.deleteObject(
            any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class)))
        .thenThrow(new RuntimeException("s3 boom"));
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(InvalidAvatarException.class)
        .hasMessageContaining("exceeds maxBytes");
  }

  @Test
  void commitThrowsWhenUserMissing() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.commitUpload(1L, "avatars/1/x.jpg"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void commitSetsAvatarAndDeletesPreviousKey() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateAvatar("https://old", "avatars/1/old.jpg");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    AvatarService.CommitResult r = service.commitUpload(1L, "avatars/1/new.jpg");
    assertThat(r.avatarUrl()).isEqualTo("https://cdn.example.com/avatars/1/new.jpg");
    assertThat(user.getAvatarKey()).isEqualTo("avatars/1/new.jpg");
    verify(s3Client, never())
        .deleteObject(
            org.mockito.ArgumentMatchers
                .<software.amazon.awssdk.services.s3.model.DeleteObjectRequest>argThat(
                    req -> req != null && req.key().equals("avatars/1/new.jpg")));
  }

  @Test
  void commitSwallowsPreviousDeleteFailure() {
    HeadObjectResponse head = HeadObjectResponse.builder().contentLength(100L).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head);
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.updateAvatar("https://old", "avatars/1/old.jpg");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(s3Client.deleteObject(
            any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class)))
        .thenThrow(new RuntimeException("s3 boom"));
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
    verify(s3Client)
        .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
  }

  @Test
  void clearAvatarOnUserWithoutPreviousSkipsDelete() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    service.clearAvatar(1L);
    verify(s3Client, never())
        .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
  }

  @Test
  void clearAvatarThrowsWhenUserMissing() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.clearAvatar(1L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void publicUrlFallsBackToStandardS3HostWhenCdnBlank() throws Exception {
    AvatarProperties noCdn = new AvatarProperties("bucket", "ap-northeast-2", null, 300, 1024);
    AvatarService svc = new AvatarService(userRepository, noCdn, presigner, s3Client);
    PresignedPutObjectRequest signed = mockSigned("https://s3/put");
    when(presigner.presignPutObject(
            any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
        .thenReturn(signed);
    AvatarService.PresignResult r = svc.presignUpload(1L, "image/png");
    assertThat(r.publicUrl()).startsWith("https://bucket.s3.ap-northeast-2.amazonaws.com/");
  }

  @Test
  void publicUrlStripsTrailingSlashOnBase() throws Exception {
    AvatarProperties slashed =
        new AvatarProperties("bucket", "ap-northeast-2", "https://cdn.example.com/", 300, 1024);
    AvatarService svc = new AvatarService(userRepository, slashed, presigner, s3Client);
    PresignedPutObjectRequest signed = mockSigned("https://s3/put");
    when(presigner.presignPutObject(
            any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
        .thenReturn(signed);
    AvatarService.PresignResult r = svc.presignUpload(1L, "image/webp");
    assertThat(r.publicUrl()).startsWith("https://cdn.example.com/avatars/1/").endsWith(".webp");
  }

  private static PresignedPutObjectRequest mockSigned(String url) throws Exception {
    PresignedPutObjectRequest signed = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
    when(signed.url()).thenReturn(URI.create(url).toURL());
    return signed;
  }
}
