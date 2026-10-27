package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.CommentLikedEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.event.HighlightReplyLikedEvent;
import com.example.short_link.common.event.NotesEmbeddedEvent;
import com.example.short_link.common.event.PostHighlightedEvent;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.common.event.PostReadMilestoneEvent;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Push;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Row;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-06-07T00:00:00Z");

  @Mock private NotificationFollowerReader followerReader;

  private final StoredNotices stored = new StoredNotices();

  private PostNotificationListener listener() {
    return new PostNotificationListener(stored.useCase(), followerReader, stored.userReader);
  }

  private static String today(String type, long subject) {
    return type + ":" + subject + ":" + LocalDate.now(ZoneOffset.UTC);
  }

  private static String authorOf(Row row) {
    return ((NotificationPostRef) row.payload()).authorUsername();
  }

  @Test
  void theFirstReaderAndLaterMilestonesReachTheAuthorWithoutAnActor() {
    listener().onPostReadMilestone(new PostReadMilestoneEvent(7L, 30L, "3", "제목", 1, AT));
    listener().onPostReadMilestone(new PostReadMilestoneEvent(7L, 30L, "3", "제목", 100, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                7L,
                NotificationType.POST_FIRST_READER,
                null,
                null,
                new NotificationPostRef(30L, "3", "제목", null, null, null, null),
                null),
            new Row(
                7L,
                NotificationType.POST_READ_MILESTONE,
                null,
                null,
                new NotificationPostRef(30L, "3", "제목", null, null, null, 100),
                null));
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void replyRecordsReplyNotificationCarryingPostAuthorHandle() {
    stored.user(9L, "replier");

    listener()
        .onCommentReply(
            new CommentReplyEvent(3L, 9L, 10L, "the-post", "The Post", "owner", 77L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                3L,
                NotificationType.REPLY,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", 77L, null),
                null));
    assertThat(stored.pushes())
        .containsExactly(
            new Push(
                List.of(3L),
                "REPLY",
                new PushRoute("replier", "owner", "the-post", null, null, 77L, null, null)));
  }

  @Test
  void selfReplyIsSkipped() {
    listener().onCommentReply(new CommentReplyEvent(9L, 9L, 10L, "s", "t", "o", 77L, AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void mentionRecordsMentionNotificationCarryingPostAuthorHandle() {
    listener()
        .onCommentMention(
            new CommentMentionEvent(5L, 9L, 10L, "the-post", "The Post", "owner", 78L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                5L,
                NotificationType.MENTION,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", 78L, null),
                null));
  }

  @Test
  void highlightReplyRecordsReplyNotificationCarryingPostAuthorHandle() {
    listener()
        .onHighlightReply(
            new HighlightReplyEvent(3L, 9L, 10L, "the-post", "The Post", "owner", 41L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                3L,
                NotificationType.REPLY,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", null, 41L),
                null));
  }

  @Test
  void selfHighlightReplyIsSkipped() {
    listener().onHighlightReply(new HighlightReplyEvent(9L, 9L, 10L, "s", "t", "o", 41L, AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void highlightMentionRecordsMentionNotificationCarryingPostAuthorHandle() {
    listener()
        .onHighlightMention(
            new HighlightMentionEvent(5L, 9L, 10L, "the-post", "The Post", "owner", 42L, AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                5L,
                NotificationType.MENTION,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", null, 42L),
                null));
  }

  @Test
  void postPublishedFansOutOneNewPostNotificationPerFollower() {
    stored.user(7L, "writer").user(1L, "a").user(2L, "b").user(3L, "c");
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of(1L, 2L, 3L));

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "the-post", "The Post", AT));

    NotificationPostRef post = new NotificationPostRef(10L, "the-post", "The Post", null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(1L, NotificationType.NEW_POST, 7L, null, post, null),
            new Row(2L, NotificationType.NEW_POST, 7L, null, post, null),
            new Row(3L, NotificationType.NEW_POST, 7L, null, post, null));
    assertThat(stored.pushes())
        .containsExactly(
            new Push(
                List.of(1L, 2L, 3L),
                "NEW_POST",
                new PushRoute("writer", "writer", "the-post", null, null, null, null, null)));
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void postPublishedWithNoFollowersIsNoOp() {
    when(followerReader.followerIdsOf(7L)).thenReturn(List.of());

    listener().onPostPublished(new PostPublishedEvent(7L, 10L, "s", "t", AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.userLookups()).isZero();
  }

  @Test
  void aMutedMembersRepliesAndMentionsStayQuiet() {
    stored.silence(3L, 9L);

    listener().onCommentReply(new CommentReplyEvent(3L, 9L, 10L, "s", "t", "owner", 77L, AT));
    listener().onCommentMention(new CommentMentionEvent(3L, 9L, 10L, "s", "t", "owner", 77L, AT));
    listener().onHighlightReply(new HighlightReplyEvent(3L, 9L, 10L, "s", "t", "owner", 42L, AT));
    listener()
        .onHighlightMention(new HighlightMentionEvent(3L, 9L, 10L, "s", "t", "owner", 42L, AT));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.pushes()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(4);
  }

  @Test
  void commentLikesGroupByCommentAndDayAndOpenTheCommentOnSomeoneElsesPost() {
    stored.user(5L, "owner").user(9L, "liker");

    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "the-post", "The Post", 5L, 77L));
    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "the-post", "The Post", 5L, 77L));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                3L,
                NotificationType.COMMENT_LIKE,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", 77L, null),
                today("COMMENT_LIKE", 77L)));
    assertThat(stored.pushes())
        .containsExactly(
            new Push(
                List.of(3L),
                "COMMENT_LIKE",
                new PushRoute("liker", "owner", "the-post", null, null, 77L, null, null)));
  }

  @Test
  void onlyALikeOnSomeoneElsesPostLooksUpThePostsOwner() {
    stored.user(5L, "owner");

    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "s", "t", 3L, 77L));
    int perNotice = stored.userLookups();
    listener().onCommentLiked(new CommentLikedEvent(4L, 9L, 10L, "s", "t", 5L, 78L));

    assertThat(stored.userLookups()).isEqualTo(perNotice + perNotice + 1);
    assertThat(stored.rows())
        .extracting(PostNotificationListenerTest::authorOf)
        .containsExactly(null, "owner");
  }

  @Test
  void selfAndMutedCommentLikesAreSkipped() {
    stored.silence(3L, 8L);

    listener().onCommentLiked(new CommentLikedEvent(3L, 3L, 10L, "s", "t", 3L, 77L));
    listener().onCommentLiked(new CommentLikedEvent(3L, 8L, 10L, "s", "t", 3L, 77L));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(1);
  }

  @Test
  void aSilencedLikeOnSomeoneElsesPostStoresAndPushesNothing() {
    stored.silence(3L, 8L);

    listener().onCommentLiked(new CommentLikedEvent(3L, 8L, 10L, "s", "t", 5L, 77L));
    listener()
        .onHighlightReplyLiked(new HighlightReplyLikedEvent(3L, 8L, 10L, "s", "t", 5L, 42L, 77L));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.pushes()).isEmpty();
  }

  @Test
  void aHighlightReplyLikeIsACommentLikeThatOpensTheThreadAndGroupsApartFromComments() {
    stored.user(5L, "owner");

    listener()
        .onHighlightReplyLiked(
            new HighlightReplyLikedEvent(3L, 9L, 10L, "the-post", "The Post", 5L, 42L, 77L));
    listener().onCommentLiked(new CommentLikedEvent(3L, 9L, 10L, "the-post", "The Post", 5L, 77L));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                3L,
                NotificationType.COMMENT_LIKE,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", null, 42L),
                "COMMENT_LIKE:hr:77:" + LocalDate.now(ZoneOffset.UTC)),
            new Row(
                3L,
                NotificationType.COMMENT_LIKE,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "owner", 77L, null),
                today("COMMENT_LIKE", 77L)));
  }

  @Test
  void selfAndMutedHighlightReplyLikesAreSkipped() {
    stored.silence(3L, 8L);

    listener()
        .onHighlightReplyLiked(new HighlightReplyLikedEvent(3L, 3L, 10L, "s", "t", 3L, 42L, 77L));
    listener()
        .onHighlightReplyLiked(new HighlightReplyLikedEvent(3L, 8L, 10L, "s", "t", 3L, 42L, 77L));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(1);
  }

  @Test
  void highlightsGroupByPostAndDayAndOpenTheNewestHighlight() {
    listener()
        .onPostHighlighted(new PostHighlightedEvent(3L, 9L, 10L, "the-post", "The Post", 42L));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                3L,
                NotificationType.HIGHLIGHT,
                9L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", null, null, 42L),
                today("HIGHLIGHT", 10L)));
  }

  @Test
  void selfAndMutedHighlightsAreSkipped() {
    stored.silence(3L, 8L);

    listener().onPostHighlighted(new PostHighlightedEvent(3L, 3L, 10L, "s", "t", 42L));
    listener().onPostHighlighted(new PostHighlightedEvent(3L, 8L, 10L, "s", "t", 42L));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(1);
  }

  @Test
  void notesQuotedInAPostTellTheirAuthorsExceptThePostsOwnAndMutedOnes() {
    stored.user(7L, "writer");
    stored.silence(6L, 7L);
    when(followerReader.shareableNoteAuthorsOf(Set.of(1L, 2L, 3L))).thenReturn(List.of(4L, 7L, 6L));

    listener()
        .onNotesEmbedded(
            new NotesEmbeddedEvent(7L, 10L, "the-post", "The Post", Set.of(1L, 2L, 3L)));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                4L,
                NotificationType.NOTE_EMBED,
                7L,
                null,
                new NotificationPostRef(10L, "the-post", "The Post", "writer"),
                today("NOTE_EMBED", 10L)));
  }

  @Test
  void aPostQuotingOnlyItsOwnNotesTellsNoOne() {
    when(followerReader.shareableNoteAuthorsOf(Set.of(1L))).thenReturn(List.of(7L));

    listener().onNotesEmbedded(new NotesEmbeddedEvent(7L, 10L, "s", "t", Set.of(1L)));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.userLookups()).isZero();
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void aPostQuotingOnlyNotesOfPeopleWhoSilencedItsWriterTellsNoOne() {
    stored.silence(6L, 7L);
    when(followerReader.shareableNoteAuthorsOf(Set.of(1L))).thenReturn(List.of(6L));

    listener().onNotesEmbedded(new NotesEmbeddedEvent(7L, 10L, "s", "t", Set.of(1L)));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.pushes()).isEmpty();
  }
}
