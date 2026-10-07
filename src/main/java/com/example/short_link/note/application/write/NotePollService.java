package com.example.short_link.note.application.write;

import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.note.RemoteNotePollVotes;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.note.application.read.NotePolls;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotePollService implements RemoteNotePollVotes {

  private static final int CLOSE_BATCH = 100;

  private final NoteRepository notes;
  private final NotePollRepository polls;
  private final UserBlockChecker blocks;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public NoteView.Poll vote(Long userId, Long noteId, List<Integer> choices) {
    NoteEntity note =
        notes
            .findById(noteId)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId));
    if (note.getVisibility().restricted()
        && !note.isOwnedBy(userId)
        && !notes.visibleTo(userId, Set.of(noteId)).contains(noteId)) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_FOUND, noteId);
    }
    if (!note.hasPoll()) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_NOT_FOUND, noteId);
    }
    if (note.isOwnedBy(userId)) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_OWN);
    }
    Instant now = clock.instant();
    if (note.pollEndedBy(now)) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_ENDED);
    }
    int picked = mask(note, choices);
    if (blocks.isBlocked(note.getUserId(), userId) || blocks.isBlocked(userId, note.getUserId())) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_BLOCKED);
    }
    if (!polls.vote(noteId, userId, picked)) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_ALREADY_VOTED);
    }
    NotePollTally tally =
        polls.tallies(List.of(noteId), userId).getOrDefault(noteId, NotePollTally.NONE);
    return NotePolls.view(note, tally, userId, now);
  }

  @Override
  @Transactional
  public boolean recordRemoteVote(Long noteId, Long remoteActorId, String optionTitle) {
    NoteEntity note = notes.findById(noteId).orElse(null);
    if (note == null || !note.hasPoll() || note.pollEndedBy(clock.instant())) {
      return false;
    }
    int index = note.pollOptions().indexOf(optionTitle == null ? "" : optionTitle.strip());
    return index >= 0 && polls.voteRemote(noteId, remoteActorId, 1 << index, note.isPollMultiple());
  }

  @Transactional
  public int closeDue() {
    Instant now = clock.instant();
    List<NoteEntity> due = polls.due(now, CLOSE_BATCH);
    if (due.isEmpty()) {
      return 0;
    }
    List<Long> ids = due.stream().map(NoteEntity::getId).toList();
    polls.close(ids, now);
    Map<Long, List<Long>> voters = polls.voterIds(ids);
    for (NoteEntity note : due) {
      events.publishEvent(
          new NotePollEndedEvent(
              note.getId(),
              note.getUserId(),
              note.excerpt(),
              voters.getOrDefault(note.getId(), List.of())));
    }
    return due.size();
  }

  private static int mask(NoteEntity note, List<Integer> choices) {
    if (choices == null || choices.isEmpty() || (!note.isPollMultiple() && choices.size() > 1)) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_CHOICES_INVALID);
    }
    int options = note.pollOptions().size();
    int mask = 0;
    for (Integer choice : choices) {
      if (choice == null || choice < 0 || choice >= options || (mask & (1 << choice)) != 0) {
        throw new NoteException(NoteErrorCode.NOTE_POLL_CHOICES_INVALID);
      }
      mask |= 1 << choice;
    }
    return mask;
  }
}
