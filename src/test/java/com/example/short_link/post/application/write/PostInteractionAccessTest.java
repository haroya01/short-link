package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.List;
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
    COMMENT_LIKE,
    HIGHLIGHT_REPLY_LIKE
  }

  @Mock private PostRepository posts;
  @Mock private CommentRepository comments;
  @Mock private PostHighlightRepository highlights;
  @Mock private PostHighlightReplyRepository replies;
  @Mock private UserModerationGuard moderation;
  @Mock private UserBlockChecker blocks;
  private PostInteractionAccess access;

  @BeforeEach
  void setUp() {
    access =
        new PostInteractionAccess(
            posts, comments, highlights, replies, moderation, blocks, PostAccessFixtures.policy());
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void moderationIsCheckedBeforeLookingUpPublicContent(Interaction interaction) {
    UserException denial = new UserException(UserErrorCode.ACCOUNT_BANNED);
    doThrow(denial).when(moderation).requireCanWrite(9L);

    assertThatThrownBy(() -> access(interaction)).isSameAs(denial);
    verifyNoInteractions(posts, comments, highlights, replies, blocks);
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
    when(blocks.isBlocked(BlockEdge.blocker(7L).blocked(9L))).thenReturn(true);
    PostErrorCode expected =
        switch (interaction) {
          case COMMENT, HIGHLIGHT_REPLY -> PostErrorCode.COMMENT_BLOCKED;
          case HIGHLIGHT, POST_LIKE, COMMENT_LIKE, HIGHLIGHT_REPLY_LIKE ->
              PostErrorCode.POST_INTERACTION_BLOCKED;
        };

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(expected);
    verify(blocks).isBlocked(BlockEdge.blocker(7L).blocked(9L));
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void aLockedPostRefusesEveryNewInteractionWithItsReason(Interaction interaction) {
    prepareTarget(interaction);
    PostEntity post = new PostEntity(7L, "followers", "Followers", "ko");
    post.publish();
    post.changeAudience(PostAudience.FOLLOWERS, null, null);
    preparePost(interaction, post);

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_LOCKED);
              assertThat(e.properties()).containsEntry("reason", PostLock.FOLLOWERS);
            });
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void aBlockedCountryRefusesEveryNewInteractionEvenFromAFollower(Interaction interaction) {
    FollowRepository follows = mock(FollowRepository.class);
    lenient().when(follows.exists(FollowEdge.follower(9L).following(7L))).thenReturn(true);
    access =
        new PostInteractionAccess(
            posts,
            comments,
            highlights,
            replies,
            moderation,
            blocks,
            PostAccessFixtures.policy(follows, "JP"));
    prepareTarget(interaction);
    PostEntity post = new PostEntity(7L, "region", "Region", "ko");
    post.publish();
    post.changeAudience(PostAudience.FOLLOWERS, null, null);
    post.blockCountries(List.of("JP"));
    preparePost(interaction, post);

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.properties()).containsEntry("reason", PostLock.REGION));
  }

  @ParameterizedTest
  @EnumSource(Interaction.class)
  void anUnlistedPostWithoutItsKeyIsNotFound(Interaction interaction) {
    prepareTarget(interaction);
    PostEntity post = new PostEntity(7L, "unlisted", "Unlisted", "ko");
    post.publish();
    post.changeAudience(PostAudience.UNLISTED, null, "the-key");
    preparePost(interaction, post);

    assertThatThrownBy(() -> access(interaction))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
    verifyNoInteractions(blocks);
  }

  @Test
  void aCommentSaidInARoomIsHeardByTheRoomsMembersAlone() {
    PostRoomReader rooms = mock(PostRoomReader.class);
    when(rooms.isMember(50L, 7L)).thenReturn(true);
    when(rooms.isMember(50L, 8L)).thenReturn(false);
    PostInteractionAccess inRoom =
        new PostInteractionAccess(
            posts,
            comments,
            highlights,
            replies,
            moderation,
            blocks,
            PostAccessFixtures.policy(rooms));
    CommentEntity comment = new CommentEntity(1L, 9L, null, "said in the room");
    comment.saidIn(50L);

    assertThat(inRoom.hears(comment, PostReader.of(7L))).isTrue();
    assertThat(inRoom.hears(comment, PostReader.of(8L))).isFalse();
  }

  @Test
  void aModeratedCommentCannotReceiveNewLikes() {
    CommentEntity deleted = new CommentEntity(42L, 3L, null, "removed");
    deleted.softDelete();
    when(comments.findById(60L)).thenReturn(Optional.of(deleted));

    assertThatThrownBy(() -> access.requireLikeableComment(PostReader.of(9L), 60L))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_NOT_FOUND);
    verifyNoInteractions(posts, highlights, blocks);
  }

  @Test
  void aTakenDownHighlightReplyCannotReceiveNewLikes() {
    PostHighlightReplyEntity takenDown = new PostHighlightReplyEntity(50L, 3L, "removed");
    takenDown.softDelete(Instant.parse("2026-10-10T00:00:00Z"));
    when(replies.findById(70L)).thenReturn(Optional.of(takenDown));

    assertThatThrownBy(() -> access.requireLikeableHighlightReply(PostReader.of(9L), 70L))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND);
    verifyNoInteractions(posts, highlights, blocks);
  }

  private void prepareTarget(Interaction interaction) {
    if (interaction == Interaction.HIGHLIGHT_REPLY
        || interaction == Interaction.HIGHLIGHT_REPLY_LIKE) {
      when(highlights.findById(50L))
          .thenReturn(Optional.of(new PostHighlightEntity(42L, 3L, 0, 0, 0, 3, "quote", null)));
    }
    if (interaction == Interaction.HIGHLIGHT_REPLY_LIKE) {
      when(replies.findById(70L))
          .thenReturn(Optional.of(new PostHighlightReplyEntity(50L, 3L, "reply")));
    } else if (interaction == Interaction.COMMENT_LIKE) {
      when(comments.findById(60L))
          .thenReturn(Optional.of(new CommentEntity(42L, 3L, null, "body")));
    }
  }

  private void access(Interaction interaction) {
    switch (interaction) {
      case COMMENT -> access.requireCommentablePost(PostReader.of(9L), 42L);
      case HIGHLIGHT_REPLY -> access.requireReplyableHighlight(PostReader.of(9L), 50L);
      case HIGHLIGHT -> access.requireInteractablePost(PostReader.of(9L), 42L);
      case POST_LIKE -> access.requireInteractablePostForUpdate(PostReader.of(9L), 42L);
      case COMMENT_LIKE -> access.requireLikeableComment(PostReader.of(9L), 60L);
      case HIGHLIGHT_REPLY_LIKE -> access.requireLikeableHighlightReply(PostReader.of(9L), 70L);
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
