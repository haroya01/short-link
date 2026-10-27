package com.example.short_link.federation.application.inbox;

import static com.example.short_link.federation.application.ActivityStreams.idOf;

import com.example.short_link.common.note.RemoteNotePollVotes;
import com.example.short_link.common.note.RemoteNoteReactions;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationFollowers;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayIntake;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// Activities we do not act on are acknowledged without a signature check and never cost a key
// fetch; anything we act on must verify first.
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

  private final InboxClassifier classifier;
  private final InboxVerifier verifier;
  private final FederationActorService localActors;
  private final FederationActorRepository actorRows;
  private final FederationFollowers followers;
  private final RemoteFollowing following;
  private final RemoteNoteReactions reactions;
  private final RemoteNotePollVotes votes;
  private final RemoteNotes remoteNotes;
  private final EarlyDeletes earlyDeletes;
  private final RemoteNoteParser noteParser;
  private final RemoteNoteIntake intake;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final MeterRegistry meters;
  private final RelaySubscriptions relays;
  private final RelayIntake relayIntake;

  public InboxOutcome receive(InboxMessage request, String inboxPublicId) {
    InboxOutcome outcome = handle(request, inboxPublicId);
    meters
        .counter(
            "federation.inbox",
            "outcome",
            outcome.kind().name().toLowerCase(Locale.ROOT),
            "reason",
            outcome.reason())
        .increment();
    if (outcome.kind() == InboxOutcome.Kind.UNAUTHORIZED
        || outcome.kind() == InboxOutcome.Kind.MALFORMED) {
      log.info("federation inbox outcome={} reason={}", outcome.kind(), outcome.reason());
    }
    return outcome;
  }

  private InboxOutcome handle(InboxMessage request, String inboxPublicId) {
    Optional<LocalActor> owner =
        inboxPublicId == null ? Optional.empty() : localActors.byPublicId(inboxPublicId);
    if (inboxPublicId != null && owner.isEmpty()) {
      return InboxOutcome.notFound();
    }
    return InboxActivity.parse(json, request.body())
        .then(
            activity ->
                classifier
                    .classify(activity, owner)
                    .then(
                        intent ->
                            authenticate(request, activity, intent)
                                .then(actor -> execute(intent, actor, activity.id()))));
  }

  private InboxStep<RemoteActorEntity> authenticate(
      InboxMessage request, InboxActivity activity, InboxIntent intent) {
    RemoteActorEntity actor;
    switch (verifier.verify(request, intent.cachedKeyOnly())) {
      case InboxVerifier.Result.Rejected rejected -> {
        if (rejected.reason().equals("unknown-key") && intent.cachedKeyOnly()) {
          return InboxStep.done(
              InboxOutcome.ignored(
                  intent instanceof InboxIntent.Forget ? "gone-actor" : "unknown-note"));
        }
        return InboxStep.done(InboxOutcome.unauthorized(rejected.reason()));
      }
      case InboxVerifier.Result.Verified verified -> actor = verified.actor();
    }
    if (actor.onSuspendedServer()) {
      return InboxStep.done(InboxOutcome.ignored("server-suspended"));
    }
    if (!actor.getActorUri().equals(activity.actorUri())) {
      return InboxStep.done(InboxOutcome.ignored("actor-mismatch"));
    }
    if (!onServerOf(actor, activity.id())) {
      return InboxStep.done(InboxOutcome.ignored("foreign-id"));
    }
    return InboxStep.next(actor);
  }

  private InboxOutcome execute(InboxIntent intent, RemoteActorEntity actor, String id) {
    return switch (intent) {
      case InboxIntent.Follow follow -> {
        followers.follow(follow.target(), actor, id);
        yield InboxOutcome.accepted("follow");
      }
      case InboxIntent.Unfollow unfollow -> unfollow(unfollow, actor);
      case InboxIntent.React react -> {
        if (!intake.mayRead(actor, react.note())) {
          yield InboxOutcome.ignored("unknown-target");
        }
        reactions.add(react.note().id(), actor.getId(), react.kind(), id);
        yield InboxOutcome.accepted(react.kind() == Kind.LIKE ? "like" : "announce");
      }
      case InboxIntent.Unreact unreact -> {
        if (unreact.noteId() != null) {
          reactions.remove(unreact.noteId(), actor.getId(), unreact.kind());
        } else {
          reactions.removeByActivity(actor.getId(), unreact.activityId());
        }
        yield InboxOutcome.accepted(unreact.kind() == Kind.LIKE ? "undo-like" : "undo-announce");
      }
      case InboxIntent.UndoById undo -> {
        followers.unfollow(actor, undo.activityId());
        reactions.removeByActivity(actor.getId(), undo.activityId());
        yield InboxOutcome.accepted("undo");
      }
      case InboxIntent.Vote vote -> {
        if (!intake.mayRead(actor, vote.poll())) {
          yield InboxOutcome.ignored("unknown-target");
        }
        yield votes.recordRemoteVote(vote.poll().id(), actor.getId(), vote.option())
            ? InboxOutcome.accepted("vote")
            : InboxOutcome.ignored("vote-rejected");
      }
      case InboxIntent.Answer answer -> answer(answer, actor);
      case InboxIntent.Receive receive -> receive(receive, actor);
      case InboxIntent.Revise revise -> revise(revise, actor);
      case InboxIntent.Retract retract ->
          remoteNotes.retract(actor.getId(), retract.uri())
              ? InboxOutcome.accepted("delete")
              : InboxOutcome.ignored("unknown-note");
      case InboxIntent.Bury buried -> {
        if (!onServerOf(actor, buried.uri())) {
          yield InboxOutcome.ignored("foreign-id");
        }
        earlyDeletes.remember(actor.getId(), buried.uri());
        yield InboxOutcome.accepted("delete-early");
      }
      case InboxIntent.Relayed relayed -> {
        String outcome = relayIntake.offer(relayed.objectUri(), actor.getActorUri());
        yield "queued".equals(outcome)
            ? InboxOutcome.accepted("relay")
            : InboxOutcome.ignored("relay-" + outcome);
      }
      case InboxIntent.Forget gone -> {
        followers.forget(actor);
        yield InboxOutcome.accepted("delete-actor");
      }
    };
  }

  private InboxOutcome unfollow(InboxIntent.Unfollow unfollow, RemoteActorEntity actor) {
    Optional<Long> userId =
        Optional.ofNullable(unfollow.targetPublicId())
            .flatMap(actorRows::findByPublicId)
            .map(FederationActorEntity::getUserId);
    if (userId.isPresent()) {
      followers.unfollow(userId.get(), actor);
    } else {
      followers.unfollow(actor, unfollow.followId());
    }
    return InboxOutcome.accepted("undo-follow");
  }

  private InboxOutcome answer(InboxIntent.Answer answer, RemoteActorEntity actor) {
    boolean known =
        urls.isRelayFollow(answer.followId()) || urls.instance().equals(answer.localActor())
            ? relays.answered(actor, answer.followId(), answer.accepted())
            : following.answered(actor, answer.followId(), answer.localActor(), answer.accepted());
    if (!known) {
      return InboxOutcome.ignored("unknown-follow");
    }
    return InboxOutcome.accepted(answer.accepted() ? "accept" : "reject");
  }

  private InboxOutcome receive(InboxIntent.Receive receive, RemoteActorEntity actor) {
    String uri = idOf(receive.object());
    if (!onServerOf(actor, uri)) {
      return InboxOutcome.ignored("foreign-id");
    }
    if (earlyDeletes.remembered(actor.getId(), uri)) {
      return InboxOutcome.ignored("deleted");
    }
    return intake
            .keep(actor, noteParser.parse(receive.object(), receive.activity()), receive.parent())
            .isPresent()
        ? InboxOutcome.accepted("note")
        : InboxOutcome.ignored("duplicate");
  }

  private InboxOutcome revise(InboxIntent.Revise revise, RemoteActorEntity actor) {
    RemoteNoteParser.Parsed note = noteParser.parse(revise.object(), revise.activity());
    return remoteNotes.revise(
            actor.getId(),
            revise.noteId(),
            new RemoteNotes.Revision(
                note.body(),
                note.contentWarning(),
                note.sensitive(),
                RemoteNoteParser.updated(revise.object()),
                note.media(),
                note.language(),
                note.shape(),
                note.quote() == null ? null : note.quote().link()))
        ? InboxOutcome.accepted("update")
        : InboxOutcome.ignored("unknown-note");
  }

  private static boolean onServerOf(RemoteActorEntity actor, String uri) {
    return Objects.equals(RemoteActorParser.host(uri), actor.getDomain());
  }
}
