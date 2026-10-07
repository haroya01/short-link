package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.NoteFilterEntity;
import com.example.short_link.note.domain.NoteFilterEntity.Action;
import com.example.short_link.note.domain.NoteFilterEntity.Context;
import com.example.short_link.note.domain.repository.NoteFilterRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteFilterService {

  private static final long MAX_SECONDS = 365L * 24 * 3600;

  private final NoteFilterRepository filters;
  private final Clock clock;

  public record Draft(
      String phrase, Boolean wholeWord, List<String> context, String action, Long expiresIn) {}

  public record FilterView(
      Long id,
      String phrase,
      boolean wholeWord,
      List<String> context,
      String action,
      Instant expiresAt) {

    static FilterView of(NoteFilterEntity filter) {
      return new FilterView(
          filter.getId(),
          filter.getPhrase(),
          filter.isWholeWord(),
          filter.contexts().stream().map(Context::apiName).toList(),
          filter.getAction().apiName(),
          filter.getExpiresAt());
    }
  }

  private record Valid(
      String phrase, boolean wholeWord, Set<Context> contexts, Action action, Instant expiresAt) {}

  @Transactional(readOnly = true)
  public List<FilterView> mine(Long userId) {
    return filters.live(userId, clock.instant()).stream().map(FilterView::of).toList();
  }

  @Transactional
  public FilterView create(Long userId, Draft draft) {
    Valid valid = validate(draft);
    if (filters.count(userId) >= NoteFilterEntity.MAX_FILTERS) {
      throw new NoteException(NoteErrorCode.NOTE_FILTER_LIMIT, NoteFilterEntity.MAX_FILTERS);
    }
    return FilterView.of(
        filters.save(
            new NoteFilterEntity(
                userId,
                valid.phrase(),
                valid.wholeWord(),
                valid.contexts(),
                valid.action(),
                valid.expiresAt())));
  }

  @Transactional
  public FilterView update(Long userId, Long id, Draft draft) {
    NoteFilterEntity filter = owned(userId, id);
    Valid valid = validate(draft);
    filter.change(
        valid.phrase(), valid.wholeWord(), valid.contexts(), valid.action(), valid.expiresAt());
    return FilterView.of(filter);
  }

  @Transactional
  public void delete(Long userId, Long id) {
    filters.delete(owned(userId, id));
  }

  private NoteFilterEntity owned(Long userId, Long id) {
    return filters
        .owned(id, userId)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_FILTER_NOT_FOUND, id));
  }

  private Valid validate(Draft draft) {
    String phrase = draft.phrase() == null ? "" : draft.phrase().strip();
    int length = phrase.codePointCount(0, phrase.length());
    Set<Context> contexts = EnumSet.noneOf(Context.class);
    for (String raw : draft.context() == null ? List.<String>of() : draft.context()) {
      Optional<Context> context = Context.parse(raw);
      if (context.isEmpty()) {
        throw invalid();
      }
      contexts.add(context.get());
    }
    Optional<Action> action =
        draft.action() == null ? Optional.of(Action.WARN) : Action.parse(draft.action());
    Long seconds = draft.expiresIn();
    if (length == 0
        || length > NoteFilterEntity.MAX_PHRASE_LENGTH
        || contexts.isEmpty()
        || action.isEmpty()
        || (seconds != null && (seconds < 60 || seconds > MAX_SECONDS))) {
      throw invalid();
    }
    Instant expiresAt =
        seconds == null
            ? null
            : clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(seconds);
    return new Valid(
        phrase, Boolean.TRUE.equals(draft.wholeWord()), contexts, action.get(), expiresAt);
  }

  private static NoteException invalid() {
    return new NoteException(NoteErrorCode.NOTE_FILTER_INVALID);
  }
}
