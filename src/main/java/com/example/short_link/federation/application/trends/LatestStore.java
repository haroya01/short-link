package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.foryou.ReachSchedule;
import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// A pulled post is kept as a relayed one is, under its author, and marked with the server it was
// pulled from. The day's cap is its own, so pulls never use up what the relays may keep; a post
// kept because a server listed it as trending has a cap of its own again. Each kept post's counts
// are tracked so they can be asked again.
@Component
@RequiredArgsConstructor
class LatestStore {

  static final String PULLED = "pulled";
  static final String TRENDED = "trended";

  private final RemoteActorResolver resolver;
  private final RemoteNoteIntake intake;
  private final RemoteNoteParser parser;
  private final FederationRelayedNoteRepository relayed;
  private final RelayTally tally;
  private final LatestProperties props;
  private final ReachProperties reachProps;
  private final RemoteNoteReachRepository reach;
  private final RemoteNotes remoteNotes;
  private final TransactionTemplate transaction;
  private final Clock clock;

  @EventListener(ApplicationReadyEvent.class)
  public void load() {
    if (props.pulling()) {
      tally.seed(PULLED, relayed.pulledSince(tally.dayStart()));
      tally.seed(TRENDED, relayed.trendedSince(tally.dayStart()));
    }
  }

  Optional<Instant> newest(String host) {
    return relayed.newestPulled(host);
  }

  long leftToday() {
    return Math.max(0, props.perDay() - tally.today(PULLED));
  }

  String keep(String host, TrendingStatusReader.Read read) {
    String outcome = leftToday() == 0 ? "pull-cap" : kept(host, read, false);
    tally.count(outcome);
    return outcome;
  }

  String keepTrended(String host, TrendingStatusReader.Read read) {
    String outcome =
        tally.today(TRENDED) >= reachProps.trendedPerDay() ? "trend-cap" : kept(host, read, true);
    tally.count(outcome);
    return outcome;
  }

  private String kept(String host, TrendingStatusReader.Read read, boolean trended) {
    String prefix = trended ? "trend" : "pull";
    RemoteNoteParser.Parsed parsed = parser.parse(read.object(), read.object());
    Optional<RemoteActorEntity> author =
        resolver
            .byActorUri(read.actorUri())
            .filter(actor -> !actor.onSuspendedServer() && read.domain().equals(actor.getDomain()));
    if (author.isEmpty()) {
      return prefix + "-no-author";
    }
    RemoteNoteParser.Parsed note =
        intake.quoted(read.quoteAccepted() ? parsed.acceptingQuote() : parsed);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Optional<Long> kept =
        transaction.execute(
            status ->
                intake
                    .relayed(author.get(), note)
                    .map(
                        noteId -> {
                          if (trended) {
                            relayed.markTrended(noteId, host, now);
                          } else {
                            relayed.markPulled(noteId, host, now);
                          }
                          reach.track(
                              noteId,
                              ReachTarget.of(read.note().uri()).orElse(null),
                              new RemoteNoteReachRepository.Counts(
                                  read.note().likeCount(),
                                  read.note().repostCount(),
                                  read.note().replyCount()),
                              now,
                              ReachSchedule.first(now));
                          remoteNotes.preview(
                              noteId, note.body(), hasMedia(note), card(read.note()));
                          return noteId;
                        }));
    if (kept == null || kept.isEmpty()) {
      return prefix + "-not-kept";
    }
    return trended ? TRENDED : PULLED;
  }

  private static boolean hasMedia(RemoteNoteParser.Parsed note) {
    return !note.media().isEmpty() || note.shape().poll() != null;
  }

  private static RemoteNotes.LinkCard card(TrendingNote note) {
    TrendingNote.LinkPreview preview = note.linkPreview();
    return preview == null
        ? null
        : new RemoteNotes.LinkCard(
            preview.url(), preview.title(), preview.description(), preview.image());
  }
}
