package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// A pulled post is kept as a relayed one is, under its author, and marked with the server it was
// pulled from. The day's cap is its own, so pulls never use up what the relays may keep; the day's
// count so far is read once, by the first pull.
@Component
class LatestStore {

  static final String PULLED = "pulled";

  private final RemoteActorResolver resolver;
  private final RemoteNoteIntake intake;
  private final RemoteNoteParser parser;
  private final FederationRelayedNoteRepository relayed;
  private final RelayTally tally;
  private final LatestProperties props;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private volatile boolean counted;

  @Autowired
  LatestStore(
      RemoteActorResolver resolver,
      RemoteNoteIntake intake,
      RemoteNoteParser parser,
      FederationRelayedNoteRepository relayed,
      RelayTally tally,
      LatestProperties props,
      TransactionTemplate transaction) {
    this(resolver, intake, parser, relayed, tally, props, transaction, Clock.systemUTC());
  }

  LatestStore(
      RemoteActorResolver resolver,
      RemoteNoteIntake intake,
      RemoteNoteParser parser,
      FederationRelayedNoteRepository relayed,
      RelayTally tally,
      LatestProperties props,
      TransactionTemplate transaction,
      Clock clock) {
    this.resolver = resolver;
    this.intake = intake;
    this.parser = parser;
    this.relayed = relayed;
    this.tally = tally;
    this.props = props;
    this.transaction = transaction;
    this.clock = clock;
  }

  Optional<Instant> newest(String host) {
    return relayed.newestPulled(host);
  }

  long room() {
    if (!counted) {
      tally.seed(PULLED, relayed.pulledSince(tally.dayStart()));
      counted = true;
    }
    return Math.max(0, props.perDay() - tally.today(PULLED));
  }

  String keep(String host, TrendingStatusReader.Read read) {
    String outcome = kept(host, read);
    tally.count(outcome);
    return outcome;
  }

  private String kept(String host, TrendingStatusReader.Read read) {
    if (room() == 0) {
      return "pull-cap";
    }
    RemoteNoteParser.Parsed note = parser.parse(read.object(), read.object());
    Optional<RemoteActorEntity> author =
        resolver
            .byActorUri(read.actorUri())
            .filter(actor -> !actor.onSuspendedServer() && read.domain().equals(actor.getDomain()));
    if (author.isEmpty()) {
      return "pull-no-author";
    }
    Optional<Long> kept =
        transaction.execute(
            status ->
                intake
                    .relayed(author.get(), note)
                    .map(
                        noteId -> {
                          relayed.markPulled(
                              noteId, host, clock.instant().truncatedTo(ChronoUnit.MICROS));
                          return noteId;
                        }));
    return kept != null && kept.isPresent() ? PULLED : "pull-not-kept";
  }
}
