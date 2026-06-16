package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeleteHighlightReplyUseCaseTest {

  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostRepository postRepository;

  private DeleteHighlightReplyUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteHighlightReplyUseCase(replyRepository, highlightRepository, postRepository);
  }

  private PostHighlightReplyEntity reply(long id, long userId) {
    PostHighlightReplyEntity r = new PostHighlightReplyEntity(50L, userId, "body");
    ReflectionTestUtils.setField(r, "id", id);
    return r;
  }

  private PostHighlightEntity highlight() {
    PostHighlightEntity h = new PostHighlightEntity(42L, 3L, 0, 0, 3, "quote", null);
    ReflectionTestUtils.setField(h, "id", 50L);
    return h;
  }

  @Test
  void authorDeletesOwnReply() {
    PostHighlightReplyEntity r = reply(1L, 9L);
    when(replyRepository.findById(1L)).thenReturn(Optional.of(r));

    useCase.execute(new DeleteHighlightReplyCommand(9L, 1L));

    verify(replyRepository).delete(r);
  }

  @Test
  void postOwnerCanDeleteOthersReply() {
    PostHighlightReplyEntity r = reply(1L, 9L); // reply by user 9
    when(replyRepository.findById(1L)).thenReturn(Optional.of(r));
    when(highlightRepository.findById(50L))
        .thenReturn(Optional.of(highlight())); // highlight post=42
    PostEntity post = new PostEntity(7L, "s", "T", "ko"); // post owner = 7
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    useCase.execute(new DeleteHighlightReplyCommand(7L, 1L)); // caller 7 = post owner

    verify(replyRepository).delete(r);
  }

  @Test
  void rejectsStranger() {
    PostHighlightReplyEntity r = reply(1L, 9L);
    when(replyRepository.findById(1L)).thenReturn(Optional.of(r));
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight()));
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));

    assertThatThrownBy(() -> useCase.execute(new DeleteHighlightReplyCommand(123L, 1L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_REPLY_PERMISSION_DENIED);
  }

  @Test
  void rejectsMissingReply() {
    when(replyRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new DeleteHighlightReplyCommand(9L, 1L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND);
  }
}
