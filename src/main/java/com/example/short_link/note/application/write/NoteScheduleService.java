package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.NoteScheduleEntity;
import com.example.short_link.note.domain.repository.NoteScheduleRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// Mastodon's scheduled statuses: the draft is kept as written and posted when due, at least five
// minutes ahead, at most 300 waiting and 25 on one UTC day. A note that can no longer be posted
// stays with the reason instead of vanishing.
@Service
public class NoteScheduleService {

  static final Duration LEAD = Duration.ofMinutes(5);
  private static final int BATCH = 50;
  private static final String UNKNOWN_FAILURE = "NOTE_SCHEDULE_FAILED";

  private final NoteScheduleRepository schedules;
  private final NoteCommandService command;
  private final JsonMapper json;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public record View(
      Long id,
      Instant scheduledAt,
      String body,
      String contentWarning,
      String visibility,
      int imageCount,
      boolean poll,
      Long inReplyToId,
      Long quotedNoteId,
      Long quotedPostId,
      String failure) {}

  @Autowired
  public NoteScheduleService(
      NoteScheduleRepository schedules,
      NoteCommandService command,
      JsonMapper json,
      TransactionTemplate transaction) {
    this(schedules, command, json, transaction, Clock.systemUTC());
  }

  NoteScheduleService(
      NoteScheduleRepository schedules,
      NoteCommandService command,
      JsonMapper json,
      TransactionTemplate transaction,
      Clock clock) {
    this.schedules = schedules;
    this.command = command;
    this.json = json;
    this.transaction = transaction;
    this.clock = clock;
  }

  @Transactional
  public View schedule(Long userId, NoteDraft draft, Instant at) {
    Instant when = ahead(at);
    Instant day = when.truncatedTo(ChronoUnit.DAYS);
    NoteScheduleRepository.Load load = schedules.load(userId, day, day.plus(1, ChronoUnit.DAYS));
    if (load.pending() >= NoteScheduleEntity.MAX_PENDING) {
      throw new NoteException(NoteErrorCode.NOTE_SCHEDULE_LIMIT, NoteScheduleEntity.MAX_PENDING);
    }
    if (load.onDay() >= NoteScheduleEntity.MAX_PER_DAY) {
      throw new NoteException(NoteErrorCode.NOTE_SCHEDULE_LIMIT, NoteScheduleEntity.MAX_PER_DAY);
    }
    command.validate(userId, draft);
    return view(
        schedules.save(new NoteScheduleEntity(userId, when, json.writeValueAsString(draft))));
  }

  @Transactional(readOnly = true)
  public List<View> list(Long userId) {
    return schedules.byUser(userId).stream().map(this::view).toList();
  }

  @Transactional
  public View reschedule(Long userId, Long id, Instant at) {
    NoteScheduleEntity row = owned(userId, id);
    row.moveTo(ahead(at));
    return view(schedules.save(row));
  }

  @Transactional
  public void cancel(Long userId, Long id) {
    schedules.delete(owned(userId, id));
  }

  @Transactional
  public void forget(Long userId) {
    schedules.deleteAllForUser(userId);
  }

  public int publishDue() {
    int published = 0;
    for (Long id : schedules.due(clock.instant(), BATCH)) {
      try {
        if (Boolean.TRUE.equals(transaction.execute(status -> publish(id)))) {
          published++;
        }
      } catch (RuntimeException e) {
        String code =
            e instanceof NoteException failed ? failed.errorCode().name() : UNKNOWN_FAILURE;
        transaction.executeWithoutResult(
            status ->
                schedules
                    .findById(id)
                    .ifPresent(
                        row -> {
                          row.fail(code);
                          schedules.save(row);
                        }));
      }
    }
    return published;
  }

  private boolean publish(Long id) {
    Optional<NoteScheduleEntity> row = schedules.findById(id);
    if (row.isEmpty() || row.get().getFailure() != null) {
      return false;
    }
    command.create(row.get().getUserId(), json.readValue(row.get().getDraft(), NoteDraft.class));
    schedules.delete(row.get());
    return true;
  }

  private Instant ahead(Instant at) {
    if (at == null || at.isBefore(clock.instant().plus(LEAD))) {
      throw new NoteException(NoteErrorCode.NOTE_SCHEDULE_TOO_SOON);
    }
    return at.truncatedTo(ChronoUnit.MICROS);
  }

  private NoteScheduleEntity owned(Long userId, Long id) {
    return schedules
        .owned(id, userId)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_SCHEDULE_NOT_FOUND, id));
  }

  private View view(NoteScheduleEntity row) {
    NoteDraft draft = json.readValue(row.getDraft(), NoteDraft.class);
    return new View(
        row.getId(),
        row.getPublishAt(),
        draft.body(),
        draft.contentWarning(),
        draft.visibility(),
        draft.images() == null ? 0 : draft.images().size(),
        draft.poll() != null,
        draft.inReplyToId(),
        draft.quotedNoteId(),
        draft.quotedPostId(),
        row.getFailure());
  }
}
