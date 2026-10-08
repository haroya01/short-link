package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.CommentLikedEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.event.NotesEmbeddedEvent;
import com.example.short_link.common.event.PostHighlightedEvent;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  @Mock private RecordBlogNotificationUseCase recordUseCase;
  @Mock private NotificationFollowerReader followerReader;
  @Mock private NotificationUserReader userReader;
  @Mock private UserBlockChecker blocks;

  private PostNotificationListener listener() {
    return new PostNotificationListener(recordUseCase, followerReader, userReader, blocks);
  }

  private static String today(String type, long subject) {
    return type + ":" + subject + ":" + LocalDate.now(ZoneOffset.UTC);
  }

  @Test
  void replyRecordsReplyNotificationCarryingPostAuthorHandle() {
    listener()
        .onCommentReply(
            new CommentReplyEvent(3L, 9L, 10L, "the-post", "The Post", "owner", 77L, AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(3L), eq(NotificationType.REPLY), eq(9L), post.capture());
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().commentId()).isEqualTo(77L);
    assertThat(post.getValue().highlightId()).isNull();
  }

  @Test
  void selfReplyIsSkipped() {
    listener().onCommentReply(new CommentReplyEvent(9L, 9L, 10L, "s", "t", "o", 77L, AT));

    verify(recordUseCase, never()).record(any(), any(), any(), any());
  }

  @Test
  void mentionRecordsMentionNotificationCarryingPostAuthorHandle() {
    listener()
        .onCommentMention(
            new CommentMentionEvent(5L, 9L, 10L, "the-post", "The Post", "owner", 78L, AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(5L), eq(NotificationType.MENTION), eq(9L), post.capture());
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().commentId()).isEqualTo(78L);
    assertThat(post.getValue().highlightId()).isNull();
  }

  @Test
  void highlightReplyRecordsReplyNotificationCarryingPostAuthorHandle() {
    listener()
        .onHighlightReply(
            new HighlightReplyEvent(3L, 9L, 10L, "the-post", "The Post", "owner", 41L, AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(3L), eq(NotificationType.REPLY), eq(9L), post.capture());
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().highlightId()).isEqualTo(41L);
    assertThat(post.getValue().commentId()).isNull();
  }

  @Test
  void selfHighlightReplyIsSkipped() {
    listener().onHighlightReply(new HighlightReplyEvent(9L, 9L, 10L, "s", "t", "o", 41L, AT));

    verify(recordUseCase, never()).record(any(), any(), any(), any());
  }

  @Test
  void highlightMentionRecordsMentionNotificationCarryingPostAuthorHandle() {
    listener()
        .onHighlightMention(
            new HighlightMentionEvent(5L, 9L, 10L, "the-post", "The Post", "owner", 42L, AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase).record(eq(5L), eq(NotificationType.MENTION), eq(9L), post.capture());
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().highlightId()).isEqualTo(42L);
    assertThat(post.getValue().commentId()).isNull();
  }

  @Test
  void postPublishedFansOutOneNewPostNotificationPerFollower() {
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of(1L, 2L, 3L));

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "the-post", "The Post", AT));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .recordForEach(
            eq(List.of(1L, 2L, 3L)), eq(NotificationType.NEW_POST), eq(7L), post.capture());
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().authorUsername()).isNull();
  }

  @Test
  void postPublishedWithNoFollowersIsNoOp() {
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of());

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "s", "t", AT));

    verify(recordUseCase, never()).recordForEach(any(), any(), any(), any());
  }

  @Test
  void aMutedMembersRepliesAndMentionsStayQuiet() {
    when(blocks.silences(3L, 9L)).thenReturn(true);

    listener().onCommentReply(new CommentReplyEvent(3L, 9L, 10L, "s", "t", "owner", 77L, AT));
    listener().onCommentMention(new CommentMentionEvent(3L, 9L, 10L, "s", "t", "owner", 77L, AT));
    listener().onHighlightReply(new HighlightReplyEvent(3L, 9L, 10L, "s", "t", "owner", 42L, AT));
    listener()
        .onHighlightMention(new HighlightMentionEvent(3L, 9L, 10L, "s", "t", "owner", 42L, AT));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void commentLikesGroupByCommentAndDayAndOpenTheCommentOnSomeoneElsesPost() {
    when(userReader.findById(5L)).thenReturn(Optional.of(new NotificationUser(5L, "owner", "ko")));

    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "the-post", "The Post", 5L, 77L));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(
            eq(3L),
            eq(NotificationType.COMMENT_LIKE),
            eq(9L),
            isNull(),
            post.capture(),
            eq(today("COMMENT_LIKE", 77L)));
    assertThat(post.getValue().authorUsername()).isEqualTo("owner");
    assertThat(post.getValue().commentId()).isEqualTo(77L);
  }

  @Test
  void aCommentLikeOnTheRecipientsOwnPostNeedsNoAuthorLookup() {
    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "s", "t", 3L, 77L));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(
            eq(3L),
            eq(NotificationType.COMMENT_LIKE),
            eq(9L),
            isNull(),
            post.capture(),
            eq(today("COMMENT_LIKE", 77L)));
    assertThat(post.getValue().authorUsername()).isNull();
    verifyNoInteractions(userReader);
  }

  @Test
  void selfAndMutedCommentLikesAreSkipped() {
    when(blocks.silences(3L, 8L)).thenReturn(true);

    listener().onCommentLiked(new CommentLikedEvent(3L, 3L, 10L, "s", "t", 3L, 77L));
    listener().onCommentLiked(new CommentLikedEvent(3L, 8L, 10L, "s", "t", 3L, 77L));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void highlightsGroupByPostAndDayAndOpenTheNewestHighlight() {
    listener()
        .onPostHighlighted(new PostHighlightedEvent(3L, 9L, 10L, "the-post", "The Post", 42L));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(
            eq(3L),
            eq(NotificationType.HIGHLIGHT),
            eq(9L),
            isNull(),
            post.capture(),
            eq(today("HIGHLIGHT", 10L)));
    assertThat(post.getValue().highlightId()).isEqualTo(42L);
    assertThat(post.getValue().slug()).isEqualTo("the-post");
  }

  @Test
  void selfAndMutedHighlightsAreSkipped() {
    when(blocks.silences(3L, 8L)).thenReturn(true);

    listener().onPostHighlighted(new PostHighlightedEvent(3L, 3L, 10L, "s", "t", 42L));
    listener().onPostHighlighted(new PostHighlightedEvent(3L, 8L, 10L, "s", "t", 42L));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void notesQuotedInAPostTellTheirAuthorsExceptThePostsOwnAndMutedOnes() {
    when(followerReader.shareableNoteAuthorsOf(Set.of(1L, 2L, 3L))).thenReturn(List.of(4L, 7L, 6L));
    when(blocks.silences(4L, 7L)).thenReturn(false);
    when(blocks.silences(6L, 7L)).thenReturn(true);
    when(userReader.findById(7L)).thenReturn(Optional.of(new NotificationUser(7L, "writer", "ko")));

    listener()
        .onNotesEmbedded(
            new NotesEmbeddedEvent(7L, 10L, "the-post", "The Post", Set.of(1L, 2L, 3L)));

    ArgumentCaptor<NotificationPostRef> post = ArgumentCaptor.forClass(NotificationPostRef.class);
    verify(recordUseCase)
        .record(
            eq(4L),
            eq(NotificationType.NOTE_EMBED),
            eq(7L),
            isNull(),
            post.capture(),
            eq(today("NOTE_EMBED", 10L)));
    verify(recordUseCase, never()).record(eq(7L), any(), any(), any(), any(), any());
    verify(recordUseCase, never()).record(eq(6L), any(), any(), any(), any(), any());
    assertThat(post.getValue().slug()).isEqualTo("the-post");
    assertThat(post.getValue().authorUsername()).isEqualTo("writer");
  }

  @Test
  void aPostQuotingOnlyItsOwnNotesTellsNoOne() {
    when(followerReader.shareableNoteAuthorsOf(Set.of(1L))).thenReturn(List.of(7L));

    listener().onNotesEmbedded(new NotesEmbeddedEvent(7L, 10L, "s", "t", Set.of(1L)));

    verifyNoInteractions(recordUseCase, userReader);
  }
}
