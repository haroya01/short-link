package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.common.event.HighlightReplyLikedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.HighlightReplyLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.HighlightReplyLikeRepository;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LikeHighlightReplyUseCaseTest {

  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private HighlightReplyLikeRepository likeRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostRepository postRepository;
  @Mock private ApplicationEventPublisher events;

  private LikeHighlightReplyUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new LikeHighlightReplyUseCase(
            replyRepository,
            likeRepository,
            new PostInteractionAccess(
                postRepository,
                Mockito.mock(CommentRepository.class),
                highlightRepository,
                replyRepository,
                Mockito.mock(UserModerationGuard.class),
                Mockito.mock(UserBlockChecker.class)),
            events);
  }

  private void replyOnPublishedPost() {
    PostEntity post = new PostEntity(5L, "post", "Title", "ko");
    post.publish();
    ReflectionTestUtils.setField(post, "id", 3L);
    PostHighlightEntity highlight = new PostHighlightEntity(3L, 6L, 0, 0, 0, 3, "quote", null);
    ReflectionTestUtils.setField(highlight, "id", 50L);
    Mockito.when(replyRepository.findById(10L))
        .thenReturn(Optional.of(new PostHighlightReplyEntity(50L, 7L, "reply")));
    Mockito.when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight));
    Mockito.when(postRepository.findById(3L)).thenReturn(Optional.of(post));
  }

  @Test
  void aNewLikeTellsTheReplysWriterAndAnswersTheCount() {
    replyOnPublishedPost();
    Mockito.when(likeRepository.insertIgnore(10L, 9L)).thenReturn(1);
    Mockito.when(likeRepository.countByReplyId(10L)).thenReturn(4L);

    HighlightReplyLikeStatus status = useCase.like(9L, 10L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(4L);
    Mockito.verify(events)
        .publishEvent(new HighlightReplyLikedEvent(7L, 9L, 3L, "post", "Title", 5L, 50L, 10L));
  }

  @Test
  void aRepeatedLikeAnswersLikedWithoutTellingAnyoneAgain() {
    replyOnPublishedPost();
    Mockito.when(likeRepository.insertIgnore(10L, 9L)).thenReturn(0);
    Mockito.when(likeRepository.countByReplyId(10L)).thenReturn(4L);

    HighlightReplyLikeStatus status = useCase.like(9L, 10L);

    assertThat(status.liked()).isTrue();
    assertThat(status.likeCount()).isEqualTo(4L);
    Mockito.verifyNoInteractions(events);
  }

  @Test
  void unlikeRemovesTheReadersLikeAndAnswersTheCount() {
    Mockito.when(replyRepository.findById(10L))
        .thenReturn(Optional.of(new PostHighlightReplyEntity(50L, 7L, "reply")));
    Mockito.when(likeRepository.countByReplyId(10L)).thenReturn(3L);

    HighlightReplyLikeStatus status = useCase.unlike(9L, 10L);

    assertThat(status.liked()).isFalse();
    assertThat(status.likeCount()).isEqualTo(3L);
    Mockito.verify(likeRepository).deleteByReplyIdAndUserId(10L, 9L);
  }

  @Test
  void anUnknownReplyCannotBeLikedOrUnliked() {
    Mockito.when(replyRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.like(9L, 99L))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND);
    assertThatThrownBy(() -> useCase.unlike(9L, 99L))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND);
    Mockito.verifyNoInteractions(likeRepository, events);
  }
}
