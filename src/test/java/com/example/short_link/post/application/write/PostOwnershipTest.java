package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostOwnershipTest {

  @Mock private PostRepository postRepository;

  private PostOwnership postOwnership;

  @BeforeEach
  void setUp() {
    postOwnership = new PostOwnership(postRepository);
  }

  @Test
  void requireOwnedReturnsPost() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    assertThat(postOwnership.requireOwned(7L, 42L)).isSameAs(post);
  }

  @Test
  void notFoundThrows() {
    when(postRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postOwnership.requireOwned(7L, 42L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void foreignOwnerThrows() {
    PostEntity post = new PostEntity(9L, "other-post", "Other", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> postOwnership.requireOwned(7L, 42L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }
}
