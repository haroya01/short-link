package com.example.short_link.federation.application.relay;

import static com.example.short_link.federation.application.ActivityStreams.idsOf;

import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final TransactionTemplate transaction;
  private final Clock clock;

  @Autowired
  public RelayStore(
      RemoteActorResolver resolver,
      RemoteNoteIntake intake,
      FederationRelayedNoteRepository relayed,
      RelaySubscriptions subscriptions,
      RelayTally tally,
      RelayProperties props,
      TransactionTemplate transaction) {
    this(resolver, intake, relayed, subscriptions, tally, props, transaction, Clock.systemUTC());
  }

  RelayStore(
      RemoteActorResolver resolver,
      RemoteNoteIntake intake,
      FederationRelayedNoteRepository relayed,
      RelaySubscriptions subscriptions,
      RelayTally tally,
      RelayProperties props,
      TransactionTemplate transaction,
      Clock clock) {
    this.resolver = resolver;
    this.intake = intake;
    this.relayed = relayed;
    this.subscriptions = subscriptions;
    this.tally = tally;
    this.props = props;
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
    Long relayId = subscriptions.relayId(relay);
    Optional<Long> kept =
        transaction.execute(
            status ->
                intake
                    .relayed(author.get(), note)
                    .map(
                        noteId -> {
                          relayed.mark(
                              noteId, relayId, clock.instant().truncatedTo(ChronoUnit.MICROS));
                          return noteId;
                        }));
    return kept != null && kept.isPresent() ? STORED : "not-kept";
  }
}
