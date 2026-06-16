package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
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
class CreateHighlightUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostHighlightReplyRepository replyRepository;

  private CreateHighlightUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateHighlightUseCase(postRepository, highlightRepository, replyRepository);
  }

  private PostEntity publishedPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title", "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostHighlightEntity highlight(long id, long postId, long userId) {
    PostHighlightEntity h = new PostHighlightEntity(postId, userId, 0, 0, 0, 3, "abc", null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  @Test
  void createsHighlightOnPublishedPost() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.save(any()))
        .thenAnswer(
            inv -> {
              PostHighlightEntity e = inv.getArgument(0);
              ReflectionTestUtils.setField(e, "id", 99L);
              return e;
            });

    HighlightRef ref =
        useCase.execute(new CreateHighlightCommand(1L, 5L, 2, null, 3, 10, "hello", "  메모  "));

    assertThat(ref.id()).isEqualTo(99L);
    assertThat(ref.blockOrder()).isEqualTo(2);
    assertThat(ref.endBlockOrder()).isEqualTo(2); // 단일 블록: endBlockOrder 가 blockOrder 로 채워진다
    assertThat(ref.quote()).isEqualTo("hello");
    assertThat(ref.note()).isEqualTo("메모"); // 양끝 공백 정규화
  }

  @Test
  void multiBlockHighlightRoundTripsEndBlockOrder() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.save(any()))
        .thenAnswer(
            inv -> {
              PostHighlightEntity e = inv.getArgument(0);
              ReflectionTestUtils.setField(e, "id", 99L);
              return e;
            });

    // 블록 2 의 offset 3 에서 시작해 블록 5 의 offset 1 까지 — 여러 블록에 걸친 하이라이트
    HighlightRef ref =
        useCase.execute(new CreateHighlightCommand(1L, 5L, 2, 5, 3, 1, "hello", null));

    assertThat(ref.blockOrder()).isEqualTo(2);
    assertThat(ref.endBlockOrder()).isEqualTo(5);
  }

  @Test
  void rejectsEndBlockOrderBeforeBlockOrder() {
    assertThatThrownBy(
            () -> useCase.execute(new CreateHighlightCommand(1L, 5L, 5, 2, 0, 3, "q", null)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(highlightRepository, never()).save(any());
  }

  @Test
  void blankNoteIsStoredAsNull() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.save(any()))
        .thenAnswer(
            inv -> {
              PostHighlightEntity e = inv.getArgument(0);
              ReflectionTestUtils.setField(e, "id", 99L);
              return e;
            });

    HighlightRef ref =
        useCase.execute(new CreateHighlightCommand(1L, 5L, 0, null, 0, 5, "q", "   "));

    assertThat(ref.note()).isNull();
  }

  @Test
  void clampsQuoteToColumnCap() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    String tooLong = "x".repeat(PostHighlightEntity.MAX_QUOTE + 500);

    HighlightRef ref =
        useCase.execute(new CreateHighlightCommand(1L, 5L, 0, null, 0, 5, tooLong, null));

    assertThat(ref.quote()).hasSize(PostHighlightEntity.MAX_QUOTE);
  }

  @Test
  void rejectsMissingPost() {
    when(postRepository.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute(new CreateHighlightCommand(1L, 5L, 0, null, 0, 5, "q", null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void rejectsUnpublishedPost() {
    PostEntity draft = new PostEntity(1L, "draft", "Draft", "ko"); // not published
    ReflectionTestUtils.setField(draft, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(draft));

    assertThatThrownBy(
            () -> useCase.execute(new CreateHighlightCommand(1L, 5L, 0, null, 0, 5, "q", null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void deleteRemovesOwnHighlight() {
    PostHighlightEntity h = highlight(7L, 5L, 1L);
    when(highlightRepository.findById(7L)).thenReturn(Optional.of(h));

    useCase.delete(1L, 7L);

    verify(highlightRepository).delete(h);
  }

  @Test
  void deleteRejectsForeignHighlight() {
    when(highlightRepository.findById(7L)).thenReturn(Optional.of(highlight(7L, 5L, 2L)));

    assertThatThrownBy(() -> useCase.delete(1L, 7L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_PERMISSION_DENIED);
    verify(highlightRepository, never()).delete(any());
  }

  @Test
  void deleteThrowsWhenMissing() {
    when(highlightRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.delete(1L, 7L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_NOT_FOUND);
  }
}
