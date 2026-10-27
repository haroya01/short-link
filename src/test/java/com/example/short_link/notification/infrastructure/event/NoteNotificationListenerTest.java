package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.event.PostQuotedEvent;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Push;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Row;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteNotificationListenerTest {

  @Mock private NotificationFollowerReader followers;

  private final StoredNotices stored = new StoredNotices();

  private NoteNotificationListener listener() {
    return new NoteNotificationListener(stored.useCase(), followers);
  }

  private static String today(NotificationType type, long noteId) {
    return type.name() + ":" + noteId + ":" + LocalDate.now(ZoneOffset.UTC);
  }

  private static NoteInteractionEvent event(
      NoteInteractionEvent.Type type, Long actorUserId, Long actorRemoteId) {
    return new NoteInteractionEvent(
        type,
        9L,
        actorUserId,
        actorRemoteId,
        5L,
        "hi",
        type == NoteInteractionEvent.Type.REPLY ? 12L : null,
        type == NoteInteractionEvent.Type.REPLY ? "me too" : null,
        null);
  }

  @Test
  void likesAndRepostsGroupByNoteAndDayWhileRepliesStandAlone() {
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.LIKE, null, 7L));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.LIKE, null, 7L));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.REPOST, 2L, null));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.REPLY, 2L, null));

    NotificationNoteRef note = new NotificationNoteRef(5L, "hi", null, null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(
                9L,
                NotificationType.NOTE_LIKE,
                null,
                7L,
                note,
                today(NotificationType.NOTE_LIKE, 5L)),
            new Row(
                9L,
                NotificationType.NOTE_REPOST,
                2L,
                null,
                note,
                today(NotificationType.NOTE_REPOST, 5L)),
            new Row(
                9L,
                NotificationType.NOTE_REPLY,
                2L,
                null,
                new NotificationNoteRef(5L, "hi", 12L, "me too"),
                null));
  }

  @Test
  void yourOwnActionsAndPeopleYouBlockedOrMutedNeverNotify() {
    stored.silence(9L, 3L);

    listener()
        .onNoteInteraction(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 9L, 9L, null, 5L, "hi", null, null, null));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.QUOTE, 3L, null));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.pushes()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(1);
    assertThat(stored.userLookups()).isZero();
  }

  @Test
  void anAccountElsewhereTheRecipientSilencedIsNotHeard() {
    stored.silenceRemote(9L, 7L);

    listener().onNoteInteraction(event(NoteInteractionEvent.Type.LIKE, null, 7L));

    assertThat(stored.rows()).isEmpty();
  }

  @Test
  void aMutedConversationIsQuietEvenForAccountsElsewhere() {
    stored.muteConversation(9L, 40L);

    listener()
        .onNoteInteraction(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 9L, null, 7L, 5L, "hi", null, null, 40L));
    listener()
        .onNoteInteraction(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 9L, null, 7L, 6L, "other", null, null, 41L));

    assertThat(stored.rows())
        .extracting(Row::payload)
        .containsExactly(new NotificationNoteRef(6L, "other", null, null));
  }

  @Test
  void anEndedPollTellsItsAuthorAndEveryVoterWhoHasNotSilencedThem() {
    stored.user(7L, "pollster").user(10L, "voter").user(11L, "quiet");
    stored.silence(11L, 7L);

    listener().onPollEnded(new NotePollEndedEvent(5L, 7L, "어디서 볼까?", List.of(7L, 10L, 11L)));

    NotificationNoteRef note = new NotificationNoteRef(5L, "어디서 볼까?", null, null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(7L, NotificationType.NOTE_POLL, 7L, null, note, null),
            new Row(10L, NotificationType.NOTE_POLL, 7L, null, note, null));
    PushRoute toThePoll = new PushRoute("pollster", "pollster", null, null, null, null, null, 5L);
    assertThat(stored.pushes())
        .containsExactly(
            new Push(List.of(7L), "NOTE_POLL", toThePoll),
            new Push(List.of(10L), "NOTE_POLL", toThePoll));
    assertThat(stored.silenceChecks()).isEqualTo(2);
  }

  @Test
  void aNewNoteTellsTheFollowersWhoAskedToHearOfIt() {
    when(followers.noteSubscribersOf(7L)).thenReturn(List.of(10L, 11L));

    listener().onNoteBroadcast(new NoteBroadcastEvent(5L, 7L, "hello"));

    NotificationNoteRef note = new NotificationNoteRef(5L, "hello", null, null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(10L, NotificationType.NOTE_POST, 7L, null, note, null),
            new Row(11L, NotificationType.NOTE_POST, 7L, null, note, null));
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void aNewNoteNobodyAskedForRecordsNothing() {
    when(followers.noteSubscribersOf(7L)).thenReturn(List.of());

    listener().onNoteBroadcast(new NoteBroadcastEvent(5L, 7L, "hello"));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.userLookups()).isZero();
  }

  @Test
  void anEditTellsWhoRepostedOrQuotedTheNoteWhetherTheAuthorIsHereOrElsewhere() {
    when(followers.noteSharersOf(5L, 7L, null)).thenReturn(List.of(10L));
    when(followers.noteSharersOf(6L, null, 40L)).thenReturn(List.of(11L, 12L));

    listener().onNoteRevised(new NoteRevisedEvent(5L, 7L, null, "고친 문장"));
    listener().onNoteRevised(new NoteRevisedEvent(6L, null, 40L, "fixed"));

    NotificationNoteRef here = new NotificationNoteRef(5L, "고친 문장", null, null);
    NotificationNoteRef elsewhere = new NotificationNoteRef(6L, "fixed", null, null);
    assertThat(stored.rows())
        .containsExactly(
            new Row(10L, NotificationType.NOTE_EDIT, 7L, null, here, null),
            new Row(11L, NotificationType.NOTE_EDIT, null, 40L, elsewhere, null),
            new Row(12L, NotificationType.NOTE_EDIT, null, 40L, elsewhere, null));
    assertThat(stored.silenceChecks()).isZero();
  }

  @Test
  void anEditNobodySharedRecordsNothing() {
    when(followers.noteSharersOf(5L, 7L, null)).thenReturn(List.of());

    listener().onNoteRevised(new NoteRevisedEvent(5L, 7L, null, "x"));

    assertThat(stored.rows()).isEmpty();
  }

  @Test
  void aNoteQuotingAPostTellsThePostsAuthorAndOpensTheQuotingNote() {
    stored.user(2L, "quoter").user(9L, "writer");

    listener().onPostQuoted(new PostQuotedEvent(9L, 2L, 300L, "이 글 좋다"));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                9L,
                NotificationType.POST_QUOTE,
                2L,
                null,
                new NotificationNoteRef(300L, "이 글 좋다", null, null),
                null));
    assertThat(stored.pushes())
        .containsExactly(
            new Push(
                List.of(9L),
                "POST_QUOTE",
                new PushRoute("quoter", "quoter", null, null, null, null, null, 300L)));
  }

  @Test
  void selfAndMutedPostQuotesAreSkipped() {
    stored.silence(9L, 3L);

    listener().onPostQuoted(new PostQuotedEvent(9L, 9L, 300L, "x"));
    listener().onPostQuoted(new PostQuotedEvent(9L, 3L, 300L, "x"));

    assertThat(stored.rows()).isEmpty();
    assertThat(stored.silenceChecks()).isEqualTo(1);
  }
}
