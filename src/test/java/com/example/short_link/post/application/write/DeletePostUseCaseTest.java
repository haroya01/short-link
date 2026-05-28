package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeletePostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostRevisionRepository postRevisionRepository;

  private DeletePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new DeletePostUseCase(
            postOwnership, postRepository, postBlockRepository, postRevisionRepository);
  }

  @Test
  void deletesInCascadeOrder() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    useCase.execute(new DeletePostCommand(7L, 42L));

    InOrder order = inOrder(postBlockRepository, postRevisionRepository, postRepository);
    order.verify(postBlockRepository).deleteAllByPostId(post.getId());
    order.verify(postRevisionRepository).deleteAllByPostId(post.getId());
    order.verify(postRepository).delete(post);
  }

  @Test
  void rejectsForeignOwner() {
    when(postOwnership.requireOwned(7L, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    assertThatThrownBy(() -> useCase.execute(new DeletePostCommand(7L, 42L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }
}
