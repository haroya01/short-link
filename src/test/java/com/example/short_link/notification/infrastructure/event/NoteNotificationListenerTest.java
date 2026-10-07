package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteNotificationListenerTest {

  @Mock private RecordBlogNotificationUseCase recordUseCase;
  @Mock private UserBlockChecker blocks;
  @Mock private NotificationFollowerReader followers;

  private NoteNotificationListener listener() {
    return new NoteNotificationListener(recordUseCase, blocks, followers);
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
        type == NoteInteractionEvent.Type.REPLY ? "me too" : null);
  }

  @Test
  void likesAndRepostsGroupByNoteAndDayWhileRepliesStandAlone() {
    String today = LocalDate.now(ZoneOffset.UTC).toString();

    listener().onNoteInteraction(event(NoteInteractionEvent.Type.LIKE, null, 7L));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.REPOST, 2L, null));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.REPLY, 2L, null));

    verify(recordUseCase)
        .record(
            9L,
            NotificationType.NOTE_LIKE,
            null,
            7L,
            new NotificationNoteRef(5L, "hi", null, null),
            "NOTE_LIKE:5:" + today);
    verify(recordUseCase)
        .record(
            9L,
            NotificationType.NOTE_REPOST,
            2L,
            null,
            new NotificationNoteRef(5L, "hi", null, null),
            "NOTE_REPOST:5:" + today);
    verify(recordUseCase)
        .record(
            9L,
            NotificationType.NOTE_REPLY,
            2L,
            null,
            new NotificationNoteRef(5L, "hi", 12L, "me too"),
            null);
  }

  @Test
  void yourOwnActionsAndPeopleYouBlockedOrMutedNeverNotify() {
    when(blocks.silences(9L, 3L, null, null)).thenReturn(true);

    listener()
        .onNoteInteraction(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 9L, 9L, null, 5L, "hi", null, null));
    listener().onNoteInteraction(event(NoteInteractionEvent.Type.QUOTE, 3L, null));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void aMutedConversationIsQuietEvenForAccountsElsewhere() {
    when(blocks.silences(9L, null, 7L, 40L)).thenReturn(true);

    listener()
        .onNoteInteraction(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 9L, null, 7L, 5L, "hi", null, null, 40L));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void aFollowFromAnotherServerIsItsOwnNotice() {
    listener().onRemoteFollowed(new RemoteFollowedEvent(9L, 7L));

    ArgumentCaptor<String> group = ArgumentCaptor.forClass(String.class);
    verify(recordUseCase)
        .record(
            eq(9L),
            eq(NotificationType.REMOTE_FOLLOW),
            isNull(),
            eq(7L),
            isNull(),
            group.capture());
    assertThat(group.getValue()).isNull();
    verifyNoInteractions(blocks);
  }

  @Test
  void anEndedPollTellsItsAuthorAndEveryVoterWhoHasNotSilencedThem() {
    when(blocks.silences(10L, 7L)).thenReturn(false);
    when(blocks.silences(11L, 7L)).thenReturn(true);

    listener().onPollEnded(new NotePollEndedEvent(5L, 7L, "어디서 볼까?", List.of(7L, 10L, 11L)));

    NotificationNoteRef note = new NotificationNoteRef(5L, "어디서 볼까?", null, null);
    verify(recordUseCase).record(7L, NotificationType.NOTE_POLL, 7L, null, note, null);
    verify(recordUseCase).recordForEach(List.of(10L), NotificationType.NOTE_POLL, 7L, note);
  }

  @Test
  void aNewNoteTellsTheFollowersWhoAskedToHearOfIt() {
    when(followers.noteSubscribersOf(7L)).thenReturn(List.of(10L, 11L));

    listener().onNoteBroadcast(new NoteBroadcastEvent(5L, 7L, "hello"));

    verify(recordUseCase)
        .recordForEach(
            List.of(10L, 11L),
            NotificationType.NOTE_POST,
            7L,
            new NotificationNoteRef(5L, "hello", null, null));
  }

  @Test
  void aNewNoteNobodyAskedForRecordsNothing() {
    when(followers.noteSubscribersOf(7L)).thenReturn(List.of());

    listener().onNoteBroadcast(new NoteBroadcastEvent(5L, 7L, "hello"));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void anEditTellsWhoRepostedOrQuotedTheNoteWhetherTheAuthorIsHereOrElsewhere() {
    when(followers.noteSharersOf(5L, 7L, null)).thenReturn(List.of(10L));
    when(followers.noteSharersOf(6L, null, 40L)).thenReturn(List.of(11L, 12L));

    listener().onNoteRevised(new NoteRevisedEvent(5L, 7L, null, "고친 문장"));
    listener().onNoteRevised(new NoteRevisedEvent(6L, null, 40L, "fixed"));

    verify(recordUseCase)
        .recordForEach(
            List.of(10L),
            NotificationType.NOTE_EDIT,
            7L,
            null,
            new NotificationNoteRef(5L, "고친 문장", null, null));
    verify(recordUseCase)
        .recordForEach(
            List.of(11L, 12L),
            NotificationType.NOTE_EDIT,
            null,
            40L,
            new NotificationNoteRef(6L, "fixed", null, null));
  }

  @Test
  void anEditNobodySharedRecordsNothing() {
    when(followers.noteSharersOf(5L, 7L, null)).thenReturn(List.of());

    listener().onNoteRevised(new NoteRevisedEvent(5L, 7L, null, "x"));

    verifyNoInteractions(recordUseCase);
  }

  @Test
  void aFollowRequestNotifiesTheLockedMemberUnlessTheyMutedTheAsker() {
    when(blocks.silences(2L, null, 7L, null)).thenReturn(false);
    when(blocks.silences(2L, 3L, null, null)).thenReturn(true);

    listener().onFollowRequested(new FollowRequestedEvent(2L, null, 7L));
    listener().onFollowRequested(new FollowRequestedEvent(2L, 3L, null));

    verify(recordUseCase).record(2L, NotificationType.FOLLOW_REQUEST, null, 7L, null, null);
    org.mockito.Mockito.verifyNoMoreInteractions(recordUseCase);
  }
}
