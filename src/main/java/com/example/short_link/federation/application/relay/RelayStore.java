package com.example.short_link.federation.application.relay;

import static com.example.short_link.federation.application.ActivityStreams.idsOf;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.foryou.ReachSchedule;
import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

// A post that names a member is left to the delivery its own server makes to them: that Create
// would find the post kept already and be dropped as a duplicate, and the member never told.
@Component
public class RelayStore {

  static final String STORED = "stored";

  private final RemoteActorResolver resolver;
  private final RemoteNoteIntake intake;
  private final FederationRelayedNoteRepository relayed;
  private final RelaySubscriptions subscriptions;
  private final RelayTally tally;
  private final RelayProperties props;
  private final RemoteNoteReachRepository reach;
  private final RemoteNotes remoteNotes;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public RelayStore(
      RemoteActorResolver resolver,
      RemoteNoteIntake intake,
      FederationRelayedNoteRepository relayed,
      RelaySubscriptions subscriptions,
      RelayTally tally,
      RelayProperties props,
      RemoteNoteReachRepository reach,
      RemoteNotes remoteNotes,
      TransactionTemplate transaction,
      Clock clock) {
    this.resolver = resolver;
    this.intake = intake;
    this.relayed = relayed;
    this.subscriptions = subscriptions;
    this.tally = tally;
    this.props = props;
    this.reach = reach;
    this.remoteNotes = remoteNotes;
    this.transaction = transaction;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void load() {
    if (props.enabled()) {
      tally.seed(STORED, relayed.relayedSince(tally.dayStart()));
    }
  }

  public String keep(String host, JsonNode post, RemoteNoteParser.Parsed note, String relay) {
    if (!note.addressedPublicIds().isEmpty()) {
      return "addressed";
    }
    if (tally.today(STORED) >= props.perDay()) {
      return "store-cap";
    }
    Optional<RemoteActorEntity> author =
        idsOf(post.get("attributedTo")).stream()
            .filter(uri -> host.equals(RemoteActorParser.host(uri)))
            .findFirst()
            .flatMap(resolver::byActorUri)
            .filter(actor -> !actor.onSuspendedServer() && host.equals(actor.getDomain()));
    if (author.isEmpty()) {
      return "no-author";
    }
    RemoteNoteParser.Parsed quoted = intake.quoted(note);
    Long relayId = subscriptions.relayId(relay);
    Optional<Long> kept =
        transaction.execute(
            status ->
                intake
                    .relayed(author.get(), quoted)
                    .map(
                        noteId -> {
                          Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                          relayed.mark(noteId, relayId, now);
                          ReachTarget.of(quoted.uri())
                              .ifPresent(
                                  target ->
                                      reach.track(
                                          noteId,
                                          target,
                                          RemoteNoteReachRepository.Counts.NONE,
                                          now,
                                          ReachSchedule.first(now)));
                          remoteNotes.preview(
                              noteId,
                              quoted.body(),
                              !quoted.media().isEmpty() || quoted.shape().poll() != null,
                              null);
                          return noteId;
                        }));
    return kept != null && kept.isPresent() ? STORED : "not-kept";
  }
}
