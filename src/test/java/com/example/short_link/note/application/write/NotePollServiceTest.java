package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotePollServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @Mock private NoteRepository notes;
  @Mock private NotePollRepository polls;
  @Mock private UserBlockChecker blocks;
  @Mock private ApplicationEventPublisher events;

  private NotePollService service() {
    return new NotePollService(notes, polls, blocks, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static NoteEntity poll(Long id, boolean multiple, Instant ends) {
    NoteEntity note = new NoteEntity(7L, "어디서 볼까?", null, null);
    ReflectionTestUtils.setField(note, "id", id);
    note.attachPoll(List.of("강남", "홍대", "성수"), ends, multiple);
    return note;
  }

  private static void fails(Runnable call, NoteErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            NoteException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  @Test
  void aVoteCountsOnceAndAnswersWithTheTallyTheVoterNowSees() {
    when(notes.findById(1L)).thenReturn(Optional.of(poll(1L, false, NOW.plusSeconds(60))));
    when(polls.vote(1L, 8L, 0b010)).thenReturn(true, false);
    when(polls.tallies(List.of(1L), 8L))
        .thenReturn(Map.of(1L, new NotePollTally(3, List.of(1L, 2L, 0L, 0L), 0b010)));

    NoteView.Poll view = service().vote(8L, 1L, List.of(1));

    assertThat(view.votesCount()).isEqualTo(3);
    assertThat(view.votersCount()).isEqualTo(3);
    assertThat(view.voted()).isTrue();
    assertThat(view.ownVotes()).containsExactly(1);
    assertThat(view.options())
        .extracting(NoteView.PollOption::votesCount)
        .containsExactly(1L, 2L, 0L);
    fails(() -> service().vote(8L, 1L, List.of(0)), NoteErrorCode.NOTE_POLL_ALREADY_VOTED);
  }

  @Test
  void aMultipleChoicePollTakesSeveralDistinctOptionsAtOnce() {
    when(notes.findById(1L)).thenReturn(Optional.of(poll(1L, true, NOW.plusSeconds(60))));
    when(polls.vote(1L, 8L, 0b101)).thenReturn(true);

    service().vote(8L, 1L, List.of(0, 2));

    verify(polls).vote(1L, 8L, 0b101);
    fails(() -> service().vote(8L, 1L, List.of(0, 0)), NoteErrorCode.NOTE_POLL_CHOICES_INVALID);
  }

  @Test
  void choicesMustBeOneKnownOptionOrSeveralInAMultipleChoicePoll() {
    when(notes.findById(1L)).thenReturn(Optional.of(poll(1L, false, NOW.plusSeconds(60))));
    for (List<Integer> choices :
        java.util.Arrays.asList(null, List.<Integer>of(), List.of(0, 1), List.of(3), List.of(-1))) {
      fails(() -> service().vote(8L, 1L, choices), NoteErrorCode.NOTE_POLL_CHOICES_INVALID);
    }
    fails(
        () -> service().vote(8L, 1L, java.util.Arrays.asList((Integer) null)),
        NoteErrorCode.NOTE_POLL_CHOICES_INVALID);
    verify(polls, never()).vote(anyLong(), anyLong(), anyInt());
  }

  @Test
  void theAuthorEndedPollsNotesWithoutAPollAndBlocksAllRefuseAVote() {
    when(notes.findById(1L)).thenReturn(Optional.of(poll(1L, false, NOW.plusSeconds(60))));
    fails(() -> service().vote(7L, 1L, List.of(0)), NoteErrorCode.NOTE_POLL_OWN);

    when(notes.findById(2L)).thenReturn(Optional.of(poll(2L, false, NOW)));
    fails(() -> service().vote(8L, 2L, List.of(0)), NoteErrorCode.NOTE_POLL_ENDED);

    NoteEntity plain = new NoteEntity(7L, "그냥 노트", null, null);
    ReflectionTestUtils.setField(plain, "id", 3L);
    when(notes.findById(3L)).thenReturn(Optional.of(plain));
    fails(() -> service().vote(8L, 3L, List.of(0)), NoteErrorCode.NOTE_POLL_NOT_FOUND);

    when(blocks.isBlocked(7L, 8L)).thenReturn(true);
    fails(() -> service().vote(8L, 1L, List.of(0)), NoteErrorCode.NOTE_POLL_BLOCKED);

    when(notes.findById(4L)).thenReturn(Optional.empty());
    fails(() -> service().vote(8L, 4L, List.of(0)), NoteErrorCode.NOTE_NOT_FOUND);
    verify(polls, never()).vote(anyLong(), anyLong(), anyInt());
  }

  @Test
  void aFollowersOnlyPollIsMissingToSomeoneWhoCannotReadIt() {
    NoteEntity note = poll(1L, false, NOW.plusSeconds(60));
    note.showTo(NoteVisibility.PRIVATE);
    when(notes.findById(1L)).thenReturn(Optional.of(note));
    when(notes.visibleTo(8L, Set.of(1L))).thenReturn(Set.of());

    fails(() -> service().vote(8L, 1L, List.of(0)), NoteErrorCode.NOTE_NOT_FOUND);

    when(notes.visibleTo(9L, Set.of(1L))).thenReturn(Set.of(1L));
    when(polls.vote(1L, 9L, 1)).thenReturn(true);
    when(polls.tallies(List.of(1L), 9L))
        .thenReturn(Map.of(1L, new NotePollTally(1, List.of(1L, 0L, 0L, 0L), 1)));
    assertThat(service().vote(9L, 1L, List.of(0)).ownVotes()).containsExactly(0);
  }

  @Test
  void aRemoteVoteNamesAnOpenOptionByTitle() {
    when(notes.findById(1L)).thenReturn(Optional.of(poll(1L, true, NOW.plusSeconds(60))));
    when(polls.voteRemote(1L, 50L, 0b100, true)).thenReturn(true);

    assertThat(service().recordRemoteVote(1L, 50L, " 성수 ")).isTrue();
    assertThat(service().recordRemoteVote(1L, 50L, "판교")).isFalse();
    assertThat(service().recordRemoteVote(1L, 50L, null)).isFalse();

    when(notes.findById(2L)).thenReturn(Optional.of(poll(2L, false, NOW.minusSeconds(1))));
    assertThat(service().recordRemoteVote(2L, 50L, "강남")).isFalse();
    when(notes.findById(3L)).thenReturn(Optional.empty());
    assertThat(service().recordRemoteVote(3L, 50L, "강남")).isFalse();
    NoteEntity plain = new NoteEntity(7L, "그냥 노트", null, null);
    when(notes.findById(4L)).thenReturn(Optional.of(plain));
    assertThat(service().recordRemoteVote(4L, 50L, "강남")).isFalse();
    verify(polls).voteRemote(anyLong(), anyLong(), anyInt(), anyBoolean());
  }

  @Test
  void closingDuePollsMarksThemAndTellsTheirVoters() {
    NoteEntity first = poll(1L, false, NOW.minusSeconds(5));
    NoteEntity second = poll(2L, false, NOW.minusSeconds(1));
    when(polls.due(NOW, 100)).thenReturn(List.of(first, second));
    when(polls.voterIds(List.of(1L, 2L))).thenReturn(Map.of(1L, List.of(8L, 9L)));

    assertThat(service().closeDue()).isEqualTo(2);

    verify(polls).close(List.of(1L, 2L), NOW);
    verify(events).publishEvent(new NotePollEndedEvent(1L, 7L, "어디서 볼까?", List.of(8L, 9L)));
    verify(events).publishEvent(new NotePollEndedEvent(2L, 7L, "어디서 볼까?", List.of()));
  }

  @Test
  void nothingDueClosesNothing() {
    when(polls.due(NOW, 100)).thenReturn(List.of());

    assertThat(service().closeDue()).isZero();

    verify(polls, never()).close(any(), any());
    verifyNoInteractions(events);
  }
}
