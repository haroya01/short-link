package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeleteCommentUseCaseTest {

  @Mock private CommentRepository commentRepository;
  @Mock private PostRepository postRepository;

  private DeleteCommentUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteCommentUseCase(commentRepository, postRepository);
  }

  private CommentEntity comment(long id, long userId, Long parentId) {
    CommentEntity c = new CommentEntity(42L, userId, parentId, "body");
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  @Test
  void authorDeletesAndCascadesReplies() {
    CommentEntity top = comment(1L, 9L, null);
    when(commentRepository.findById(1L)).thenReturn(Optional.of(top));
    CommentEntity reply = comment(2L, 3L, 1L);
    when(commentRepository.findAllByParentId(1L)).thenReturn(List.of(reply));

    useCase.execute(new DeleteCommentCommand(9L, 1L));

    verify(commentRepository).delete(reply);
    verify(commentRepository).delete(top);
  }

  @Test
  void postOwnerCanDeleteOthersComment() {
    CommentEntity c = comment(1L, 9L, 5L); // a reply by user 9
    when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
    PostEntity post = new PostEntity(7L, "s", "T", "ko"); // post owner = 7
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    useCase.execute(new DeleteCommentCommand(7L, 1L)); // caller 7 = post owner

    verify(commentRepository).delete(c);
  }

  @Test
  void rejectsStranger() {
    CommentEntity c = comment(1L, 9L, null);
    when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));

    assertThatThrownBy(() -> useCase.execute(new DeleteCommentCommand(123L, 1L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_PERMISSION_DENIED);
  }
}
