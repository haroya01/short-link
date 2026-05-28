package com.example.short_link.post.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.s3.AvatarProperties;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

  @Mock private AvatarProperties props;
  @Mock private ObjectStorage objectStorage;
  @Mock private PostOwnership postOwnership;

  private PostImageService service;

  @BeforeEach
  void setUp() {
    service = new PostImageService(props, objectStorage, postOwnership);
  }

  private PostEntity ownedPost() {
    return new PostEntity(7L, "p", "P", "ko");
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
}
