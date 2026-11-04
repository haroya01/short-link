package com.example.short_link.note.application.write;

import com.example.short_link.common.link.LinkOgFetchReopener;
import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository.Candidate;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository.Refill;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteLinkPreviewRefill {

  static final Duration PACE = Duration.ofSeconds(1);

  private final NoteLinkPreviewRefillRepository refills;
  private final NoteLinkPreviewService previews;
  private final LinkPreviewReader reader;
  private final LinkOgFetchReopener ogFetches;
  private final TransactionTemplate transaction;
  private final Clock clock;

  private Refill current;
  private boolean idle;
  private Instant lastFetch = Instant.EPOCH;

  public synchronized void step() {
    if (idle) {
      return;
    }
    if (current == null) {
      current = refills.pending().orElse(null);
      idle = current == null;
    } else if (!current.started()) {
      start();
    } else {
      refillNext();
    }
  }

  private void start() {
    Refill begun = transaction.execute(this::begin);
    if (begun == null) {
      current = null;
      return;
    }
    current = begun;
    log.info(
        "note link preview refill {} started: {} notes to check, {} short links reopened",
        begun.name(),
        begun.targets(),
        begun.linksReopened());
  }

  private Refill begin(TransactionStatus status) {
    long targets = refills.countCandidates();
    int reopened = ogFetches.reopenGivenUp();
    if (!refills.start(current.name(), targets, reopened, now())) {
      status.setRollbackOnly();
      return null;
    }
    return current.begun(targets, reopened);
  }

  private void refillNext() {
    Instant now = clock.instant();
    if (now.isBefore(lastFetch.plus(PACE))) {
      return;
    }
    Optional<Candidate> next = refills.newestBelow(current.cursor());
    if (next.isEmpty()) {
      finish();
      return;
    }
    Candidate note = next.get();
    String url = NoteLinks.previewUrl(note.body(), false, false);
    if (url == null) {
      current = current.skipped(note.id());
    } else {
      lastFetch = now;
      current = current.refreshed(note.id(), refresh(note.id(), url));
    }
    refills.advance(current);
  }

  private boolean refresh(long noteId, String url) {
    try {
      reader.forget(url);
      return previews.refresh(noteId, url);
    } catch (RuntimeException failure) {
      log.warn("note link preview refill skipped note {}: {}", noteId, failure.toString());
      return false;
    }
  }

  private void finish() {
    refills.finish(current.name(), now());
    log.info(
        "note link preview refill {} finished: {} of {} notes now have a card, {} still none,"
            + " {} short links reopened",
        current.name(),
        current.filled(),
        current.targets(),
        current.missed(),
        current.linksReopened());
    current = null;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
