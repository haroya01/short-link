package com.example.short_link.federation.application.inbox;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Visibility;
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
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Activities we do not act on are acknowledged without a signature check and never cost a key
// fetch; anything we act on must verify first.
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

  private static final int MAX_URI = 512;

  private final InboxVerifier verifier;
  private final FederationActorService localActors;
  private final FederationActorRepository actorRows;
  private final FederationFollowers followers;
  private final RemoteFollowing following;
  private final NoteSnapshotReader notes;
  private final RemoteNoteReactions reactions;
  private final RemoteNotePollVotes votes;
  private final RemoteNotes remoteNotes;
  private final EarlyDeletes earlyDeletes;
  private final RemoteNoteParser noteParser;
  private final FederationFollowingRepository followingRows;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final MeterRegistry meters;

  private sealed interface Intent {
    record Follow(LocalActor target) implements Intent {}

    record Unfollow(String targetPublicId, String followId) implements Intent {}

    record React(NoteSnapshot note, Kind kind) implements Intent {}

    record Unreact(Long noteId, Kind kind, String activityId) implements Intent {}

    record UndoById(String activityId) implements Intent {}

    record Vote(NoteSnapshot poll, String option) implements Intent {}

    record Forget() implements Intent {}

    record Answer(String followId, String localActor, boolean accepted) implements Intent {}

    record Receive(JsonNode object, JsonNode activity, NoteSnapshot parent) implements Intent {}

    record Revise(JsonNode object, JsonNode activity, Long noteId) implements Intent {}

    record Retract(String uri) implements Intent {}

    record Bury(String uri) implements Intent {}
  }

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
    JsonNode activity;
    try {
      activity = json.readTree(request.body());
    } catch (JacksonException malformed) {
      return InboxOutcome.malformed("json");
    }
    if (activity == null || !activity.isObject()) {
      return InboxOutcome.malformed("json");
    }
    String type = text(activity.get("type"));
    String id = text(activity.get("id"));
    String actorUri = idOf(activity.get("actor"));
    if (type == null || id == null || actorUri == null) {
      return InboxOutcome.malformed("fields");
    }

    Intent intent;
    switch (type) {
      case "Follow" -> {
        Optional<LocalActor> target = followTarget(activity.get("object"), owner);
        if (target.isEmpty()) {
          return InboxOutcome.ignored("unknown-target");
        }
        intent = new Intent.Follow(target.get());
      }
      case "Like", "Announce" -> {
        Kind kind = reactionKind(type);
        Optional<NoteSnapshot> note =
            urls.noteIdOf(idOf(activity.get("object"))).flatMap(noteId -> federated(noteId, owner));
        if (note.isEmpty() || (kind == Kind.ANNOUNCE && !shareable(note.get()))) {
          return InboxOutcome.ignored("unknown-target");
        }
        intent = new Intent.React(note.get(), kind);
      }
      case "Undo" -> {
        JsonNode object = activity.get("object");
        String undoneId = idOf(object);
        if (object != null && object.isObject()) {
          String undoneType = text(object.get("type"));
          Kind kind = reactionKind(undoneType);
          if (kind == null && !"Follow".equals(undoneType)) {
            return InboxOutcome.ignored("unsupported");
          }
          if (!actorUri.equals(idOf(object.get("actor")))) {
            return InboxOutcome.ignored("actor-mismatch");
          }
          if (undoneId == null) {
            return InboxOutcome.malformed("fields");
          }
          String target = idOf(object.get("object"));
          intent =
              kind == null
                  ? new Intent.Unfollow(urls.publicIdOf(target).orElse(null), undoneId)
                  : new Intent.Unreact(urls.noteIdOf(target).orElse(null), kind, undoneId);
        } else {
          if (undoneId == null) {
            return InboxOutcome.malformed("fields");
          }
          intent = new Intent.UndoById(undoneId);
        }
      }
      case "Create" -> {
        JsonNode object = activity.get("object");
        if (object == null || !object.isObject() || !"Note".equals(text(object.get("type")))) {
          return InboxOutcome.ignored("unsupported");
        }
        String option = text(object.get("name"));
        String inReplyTo = idOf(object.get("inReplyTo"));
        Optional<Long> noteId = urls.noteIdOf(inReplyTo);
        if (option != null && noteId.isPresent()) {
          Optional<NoteSnapshot> poll = federated(noteId.get(), owner);
          if (poll.isEmpty()) {
            return InboxOutcome.ignored("unknown-target");
          }
          intent = new Intent.Vote(poll.get(), option);
        } else {
          if (!authoredBy(object, actorUri)) {
            return InboxOutcome.ignored("unsupported");
          }
          Optional<NoteSnapshot> parent = noteId.flatMap(notes::find);
          boolean wanted =
              noteParser.addressesUs(object, activity)
                  || parent.filter(note -> federates(note, owner)).isPresent()
                  || remoteNotes.exists(inReplyTo)
                  || followingRows.anyAcceptedFollowOf(actorUri);
          if (!wanted) {
            return InboxOutcome.ignored("unsolicited");
          }
          intent = new Intent.Receive(object, activity, parent.orElse(null));
        }
      }
      case "Update" -> {
        JsonNode object = activity.get("object");
        if (object == null || !object.isObject() || !"Note".equals(text(object.get("type")))) {
          return InboxOutcome.ignored("unsupported");
        }
        Optional<Long> kept =
            authoredBy(object, actorUri) ? remoteNotes.kept(idOf(object)) : Optional.empty();
        if (kept.isEmpty()) {
          return InboxOutcome.ignored("unknown-note");
        }
        intent = new Intent.Revise(object, activity, kept.get());
      }
      case "Accept", "Reject" -> {
        JsonNode object = activity.get("object");
        boolean embedded = object != null && object.isObject();
        String followId = idOf(object);
        String localActor = embedded ? idOf(object.get("actor")) : null;
        boolean ours =
            embedded
                ? "Follow".equals(text(object.get("type")))
                    && urls.publicIdOf(localActor).isPresent()
                : urls.isFollow(followId);
        if (!ours) {
          return InboxOutcome.ignored("unsupported");
        }
        intent = new Intent.Answer(followId, localActor, "Accept".equals(type));
      }
      case "Delete" -> {
        String deleted = idOf(activity.get("object"));
        if (actorUri.equals(deleted)) {
          intent = new Intent.Forget();
        } else if (remoteNotes.exists(deleted)) {
          intent = new Intent.Retract(deleted);
        } else if (deleted != null
            && deleted.length() <= MAX_URI
            && Objects.equals(RemoteActorParser.host(deleted), RemoteActorParser.host(actorUri))) {
          intent = new Intent.Bury(deleted);
        } else {
          return InboxOutcome.ignored("unsupported");
        }
      }
      default -> {
        return InboxOutcome.ignored("unsupported");
      }
    }

    boolean forget = intent instanceof Intent.Forget;
    boolean bury = intent instanceof Intent.Bury;
    RemoteActorEntity actor;
    switch (verifier.verify(request, forget || bury)) {
      case InboxVerifier.Result.Rejected rejected -> {
        if (rejected.reason().equals("unknown-key") && (forget || bury)) {
          return InboxOutcome.ignored(forget ? "gone-actor" : "unknown-note");
        }
        return InboxOutcome.unauthorized(rejected.reason());
      }
      case InboxVerifier.Result.Verified verified -> actor = verified.actor();
    }
    if (actor.onSuspendedServer()) {
      return InboxOutcome.ignored("server-suspended");
    }
    if (!actor.getActorUri().equals(actorUri)) {
      return InboxOutcome.ignored("actor-mismatch");
    }
    if (!Objects.equals(RemoteActorParser.host(id), actor.getDomain())) {
      return InboxOutcome.ignored("foreign-id");
    }

    return switch (intent) {
      case Intent.Follow follow -> {
        followers.follow(follow.target(), actor, id);
        yield InboxOutcome.accepted("follow");
      }
      case Intent.Unfollow unfollow -> {
        Optional<Long> userId =
            Optional.ofNullable(unfollow.targetPublicId())
                .flatMap(actorRows::findByPublicId)
                .map(FederationActorEntity::getUserId);
        if (userId.isPresent()) {
          followers.unfollow(userId.get(), actor);
        } else {
          followers.unfollow(actor, unfollow.followId());
        }
        yield InboxOutcome.accepted("undo-follow");
      }
      case Intent.React react -> {
        if (!mayRead(actor, react.note())) {
          yield InboxOutcome.ignored("unknown-target");
        }
        reactions.add(react.note().id(), actor.getId(), react.kind(), id);
        yield InboxOutcome.accepted(react.kind() == Kind.LIKE ? "like" : "announce");
      }
      case Intent.Unreact unreact -> {
        if (unreact.noteId() != null) {
          reactions.remove(unreact.noteId(), actor.getId(), unreact.kind());
        } else {
          reactions.removeByActivity(actor.getId(), unreact.activityId());
        }
        yield InboxOutcome.accepted(unreact.kind() == Kind.LIKE ? "undo-like" : "undo-announce");
      }
      case Intent.UndoById undo -> {
        followers.unfollow(actor, undo.activityId());
        reactions.removeByActivity(actor.getId(), undo.activityId());
        yield InboxOutcome.accepted("undo");
      }
      case Intent.Vote vote -> {
        if (!mayRead(actor, vote.poll())) {
          yield InboxOutcome.ignored("unknown-target");
        }
        yield votes.recordRemoteVote(vote.poll().id(), actor.getId(), vote.option())
            ? InboxOutcome.accepted("vote")
            : InboxOutcome.ignored("vote-rejected");
      }
      case Intent.Answer answer -> {
        if (!following.answered(actor, answer.followId(), answer.localActor(), answer.accepted())) {
          yield InboxOutcome.ignored("unknown-follow");
        }
        yield InboxOutcome.accepted(answer.accepted() ? "accept" : "reject");
      }
      case Intent.Receive receive -> {
        if (!Objects.equals(RemoteActorParser.host(idOf(receive.object())), actor.getDomain())) {
          yield InboxOutcome.ignored("foreign-id");
        }
        if (earlyDeletes.remembered(actor.getId(), idOf(receive.object()))) {
          yield InboxOutcome.ignored("deleted");
        }
        RemoteNoteParser.Parsed note = noteParser.parse(receive.object(), receive.activity());
        boolean answers = receive.parent() == null || mayRead(actor, receive.parent());
        List<Long> addressed =
            actorRows.findByPublicIds(note.addressedPublicIds()).stream()
                .map(FederationActorEntity::getUserId)
                .toList();
        yield remoteNotes
                .receive(
                    new RemoteNotes.Received(
                        actor.getId(),
                        note.uri(),
                        note.url() != null && note.url().length() <= MAX_URI ? note.url() : null,
                        note.body(),
                        note.contentWarning(),
                        note.sensitive(),
                        note.visibility(),
                        note.publishedAt(),
                        answers ? note.inReplyToLocalId().orElse(null) : null,
                        note.inReplyToUri(),
                        addressed,
                        note.media(),
                        note.language()))
                .isPresent()
            ? InboxOutcome.accepted("note")
            : InboxOutcome.ignored("duplicate");
      }
      case Intent.Revise revise -> {
        RemoteNoteParser.Parsed note = noteParser.parse(revise.object(), revise.activity());
        yield remoteNotes.revise(
                actor.getId(),
                revise.noteId(),
                new RemoteNotes.Revision(
                    note.body(),
                    note.contentWarning(),
                    note.sensitive(),
                    RemoteNoteParser.updated(revise.object()),
                    note.media(),
                    note.language()))
            ? InboxOutcome.accepted("update")
            : InboxOutcome.ignored("unknown-note");
      }
      case Intent.Retract retract -> {
        yield remoteNotes.retract(actor.getId(), retract.uri())
            ? InboxOutcome.accepted("delete")
            : InboxOutcome.ignored("unknown-note");
      }
      case Intent.Bury buried -> {
        if (!Objects.equals(RemoteActorParser.host(buried.uri()), actor.getDomain())) {
          yield InboxOutcome.ignored("foreign-id");
        }
        earlyDeletes.remember(actor.getId(), buried.uri());
        yield InboxOutcome.accepted("delete-early");
      }
      case Intent.Forget gone -> {
        followers.forget(actor);
        yield InboxOutcome.accepted("delete-actor");
      }
    };
  }

  // The same rule that serves the note document: it exists and its author federates. A personal
  // inbox already resolved its owner, who is usually the author.
  private Optional<NoteSnapshot> federated(Long noteId, Optional<LocalActor> owner) {
    return notes.find(noteId).filter(note -> federates(note, owner));
  }

  private boolean federates(NoteSnapshot note, Optional<LocalActor> owner) {
    return owner.filter(actor -> actor.user().id().equals(note.authorId())).isPresent()
        || localActors.byUsername(note.authorUsername()).isPresent();
  }

  private static boolean shareable(NoteSnapshot note) {
    return note.visibility() == Visibility.PUBLIC || note.visibility() == Visibility.UNLISTED;
  }

  // Mastodon's rule for who may like, answer or vote on a note: anyone on a public or unlisted
  // one, an accepted follower or a named account on a followers-only one, a named account on a
  // direct one. A named account is one the note's text mentions, as delivery reached it.
  private boolean mayRead(RemoteActorEntity actor, NoteSnapshot note) {
    if (shareable(note)) {
      return true;
    }
    boolean named =
        actor.getUsername() != null
            && Mentions.remote(note.body())
                .contains((actor.getUsername() + "@" + actor.getDomain()).toLowerCase(Locale.ROOT));
    return named
        || (note.visibility() == Visibility.PRIVATE
            && followers.accepts(note.authorId(), actor.getId()));
  }

  private static boolean authoredBy(JsonNode object, String actorUri) {
    String uri = idOf(object);
    return uri != null
        && uri.length() <= MAX_URI
        && actorUri.equals(idOf(object.get("attributedTo")));
  }

  private static Kind reactionKind(String type) {
    if ("Like".equals(type)) {
      return Kind.LIKE;
    }
    return "Announce".equals(type) ? Kind.ANNOUNCE : null;
  }

  private Optional<LocalActor> followTarget(JsonNode object, Optional<LocalActor> owner) {
    Optional<String> publicId = urls.publicIdOf(idOf(object));
    if (publicId.isEmpty()) {
      return Optional.empty();
    }
    if (owner.isPresent() && owner.get().publicId().equals(publicId.get())) {
      return owner;
    }
    return localActors.byPublicId(publicId.get());
  }
}
