package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostInteractionAccessTest {

  private enum Interaction {
    COMMENT,
    HIGHLIGHT,
    HIGHLIGHT_REPLY,
    POST_LIKE,
    COMMENT_LIKE
  }

  @Mock private PostRepository posts;
  @Mock private CommentRepository comments;
  @Mock private PostHighlightRepository highlights;
  @Mock private UserModerationGuard moderation;
  @Mock private UserBlockChecker blocks;
  private PostInteractionAccess access;

  @BeforeEach
  void setUp() {
    access = new PostInteractionAccess(posts, comments, highlights, moderation, blocks);
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void moderationIsCheckedBeforeLookingUpPublicContent(Interaction interaction) {
    UserException denial = new UserException(UserErrorCode.ACCOUNT_BANNED);
    doThrow(denial).when(moderation).requireCanWrite(9L);

    assertThatThrownBy(() -> access(interaction)).isSameAs(denial);
    verifyNoInteractions(posts, comments, highlights, blocks);
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void unpublishedSourceCannotReceiveAnyNewInteraction(Interaction interaction) {
    prepareTarget(interaction);
    preparePost(interaction, new PostEntity(7L, "draft", "Draft", "ko"));

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
    verifyNoInteractions(blocks);
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void postAuthorsBlockAppliesAlsoToThreadsOpenedByAnotherReader(Interaction interaction) {
    prepareTarget(interaction);
    PostEntity post = new PostEntity(7L, "published", "Published", "ko");
    post.publish();
    preparePost(interaction, post);
    when(blocks.isBlocked(7L, 9L)).thenReturn(true);
    PostErrorCode expected =
        switch (interaction) {
          case COMMENT, HIGHLIGHT_REPLY -> PostErrorCode.COMMENT_BLOCKED;
          case HIGHLIGHT, POST_LIKE, COMMENT_LIKE -> PostErrorCode.POST_INTERACTION_BLOCKED;
        };

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(expected);
    verify(blocks).isBlocked(7L, 9L);
  }

  @Test
  void aModeratedCommentCannotReceiveNewLikes() {
    CommentEntity deleted = new CommentEntity(42L, 3L, null, "removed");
    deleted.softDelete();
    when(comments.findById(60L)).thenReturn(Optional.of(deleted));

    assertThatThrownBy(() -> access.requireLikeableComment(9L, 60L))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_NOT_FOUND);
    verifyNoInteractions(posts, highlights, blocks);
  }

  private void prepareTarget(Interaction interaction) {
    if (interaction == Interaction.HIGHLIGHT_REPLY) {
      when(highlights.findById(50L))
          .thenReturn(Optional.of(new PostHighlightEntity(42L, 3L, 0, 0, 0, 3, "quote", null)));
    } else if (interaction == Interaction.COMMENT_LIKE) {
      when(comments.findById(60L))
          .thenReturn(Optional.of(new CommentEntity(42L, 3L, null, "body")));
    }
  }

  private void access(Interaction interaction) {
    switch (interaction) {
      case COMMENT -> access.requireCommentablePost(9L, 42L);
      case HIGHLIGHT_REPLY -> access.requireReplyableHighlight(9L, 50L);
      case HIGHLIGHT -> access.requireInteractablePost(9L, 42L);
      case POST_LIKE -> access.requireInteractablePostForUpdate(9L, 42L);
      case COMMENT_LIKE -> access.requireLikeableComment(9L, 60L);
    }
  }

  private void preparePost(Interaction interaction, PostEntity post) {
    if (interaction == Interaction.POST_LIKE) {
      when(posts.findByIdForUpdate(42L)).thenReturn(Optional.of(post));
    } else {
      when(posts.findById(42L)).thenReturn(Optional.of(post));
    }
  }
}
