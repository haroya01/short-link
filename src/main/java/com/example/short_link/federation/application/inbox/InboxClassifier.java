package com.example.short_link.federation.application.inbox;

import static com.example.short_link.federation.application.ActivityStreams.MAX_URI;
import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.idsOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// Runs before the signature is checked: it only reads, and the actor it sees is still a claim.
@Component
@RequiredArgsConstructor
class InboxClassifier {

  private final FederationUrls urls;
  private final FederationActorService localActors;
  private final NoteSnapshotReader notes;
  private final RemoteNotes remoteNotes;
  private final FederationFollowingRepository followingRows;
  private final RelaySubscriptions relays;
  private final RemoteNoteParser noteParser;

  InboxStep<InboxIntent> classify(InboxActivity activity, Optional<LocalActor> owner) {
    return switch (activity.type()) {
      case "Follow" -> follow(activity, owner);
      case "Like", "Announce" -> reaction(activity, owner);
      case "Undo" -> undo(activity);
      case "Create" -> create(activity, owner);
      case "Update" -> update(activity);
      case "Accept", "Reject" -> answer(activity);
      case "Delete" -> delete(activity);
      default -> ignored("unsupported");
    };
  }

  private InboxStep<InboxIntent> follow(InboxActivity activity, Optional<LocalActor> owner) {
    return followTarget(activity.object(), owner)
        .map(target -> act(new InboxIntent.Follow(target)))
        .orElseGet(() -> ignored("unknown-target"));
  }

  private InboxStep<InboxIntent> reaction(InboxActivity activity, Optional<LocalActor> owner) {
    Kind kind = reactionKind(activity.type());
    String objectUri = idOf(activity.object());
    if (kind == Kind.ANNOUNCE && relays.isAccepted(activity.actorUri())) {
      return act(new InboxIntent.Relayed(objectUri));
    }
    Optional<NoteSnapshot> note =
        urls.noteIdOf(objectUri).flatMap(noteId -> federated(noteId, owner));
    if (note.isEmpty() || (kind == Kind.ANNOUNCE && !note.get().visibility().shareable())) {
      return ignored("unknown-target");
    }
    return act(new InboxIntent.React(note.get(), kind));
  }

  private InboxStep<InboxIntent> undo(InboxActivity activity) {
    JsonNode object = activity.object();
    String undoneId = idOf(object);
    if (object == null || !object.isObject()) {
      return undoneId == null ? malformed() : act(new InboxIntent.UndoById(undoneId));
    }
    String undoneType = text(object.get("type"));
    Kind kind = reactionKind(undoneType);
    if (kind == null && !"Follow".equals(undoneType)) {
      return ignored("unsupported");
    }
    if (!activity.actorUri().equals(idOf(object.get("actor")))) {
      return ignored("actor-mismatch");
    }
    if (undoneId == null) {
      return malformed();
    }
    String target = idOf(object.get("object"));
    return act(
        kind == null
            ? new InboxIntent.Unfollow(urls.publicIdOf(target).orElse(null), undoneId)
            : new InboxIntent.Unreact(urls.noteIdOf(target).orElse(null), kind, undoneId));
  }

  private InboxStep<InboxIntent> create(InboxActivity activity, Optional<LocalActor> owner) {
    JsonNode object = activity.object();
    Optional<RemoteNotes.Kind> kind = RemoteNoteParser.kindOf(object);
    if (kind.isEmpty()) {
      return ignored("unsupported");
    }
    String option = kind.get() == RemoteNotes.Kind.NOTE ? text(object.get("name")) : null;
    String inReplyTo = idOf(object.get("inReplyTo"));
    Optional<Long> noteId = urls.noteIdOf(inReplyTo);
    if (option != null && noteId.isPresent()) {
      return federated(noteId.get(), owner)
          .map(poll -> act(new InboxIntent.Vote(poll, option)))
          .orElseGet(() -> ignored("unknown-target"));
    }
    if (!authoredBy(object, activity.actorUri())) {
      return ignored("unsupported");
    }
    Optional<NoteSnapshot> parent = noteId.flatMap(notes::find);
    boolean wanted =
        noteParser.addressesUs(object, activity.json())
            || parent.filter(note -> federates(note, owner)).isPresent()
            || remoteNotes.exists(inReplyTo)
            || followingRows.anyAcceptedFollowOf(activity.actorUri());
    if (!wanted) {
      return ignored("unsolicited");
    }
    return act(new InboxIntent.Receive(object, activity.json(), parent.orElse(null)));
  }

  private InboxStep<InboxIntent> update(InboxActivity activity) {
    JsonNode object = activity.object();
    if (RemoteNoteParser.kindOf(object).isEmpty()) {
      return ignored("unsupported");
    }
    Optional<Long> kept =
        authoredBy(object, activity.actorUri()) ? remoteNotes.kept(idOf(object)) : Optional.empty();
    return kept.map(noteId -> act(new InboxIntent.Revise(object, activity.json(), noteId)))
        .orElseGet(() -> ignored("unknown-note"));
  }

  private InboxStep<InboxIntent> answer(InboxActivity activity) {
    JsonNode object = activity.object();
    boolean embedded = object != null && object.isObject();
    String followId = idOf(object);
    String localActor = embedded ? idOf(object.get("actor")) : null;
    boolean ours =
        embedded
            ? "Follow".equals(text(object.get("type")))
                && (urls.publicIdOf(localActor).isPresent() || urls.instance().equals(localActor))
            : urls.isFollow(followId) || urls.isRelayFollow(followId);
    if (!ours) {
      return ignored("unsupported");
    }
    return act(new InboxIntent.Answer(followId, localActor, "Accept".equals(activity.type())));
  }

  private InboxStep<InboxIntent> delete(InboxActivity activity) {
    String deleted = idOf(activity.object());
    if (activity.actorUri().equals(deleted)) {
      return act(new InboxIntent.Forget());
    }
    if (remoteNotes.exists(deleted)) {
      return act(new InboxIntent.Retract(deleted));
    }
    if (deleted != null
        && deleted.length() <= MAX_URI
        && Objects.equals(
            RemoteActorParser.host(deleted), RemoteActorParser.host(activity.actorUri()))) {
      return act(new InboxIntent.Bury(deleted));
    }
    return ignored("unsupported");
  }

  // The same rule that serves the note document: it exists and its author federates. A personal
  // inbox already resolved its owner, who is usually the author.
  private Optional<NoteSnapshot> federated(Long noteId, Optional<LocalActor> owner) {
    return notes
        .find(noteId)
        .filter(note -> note.visibility().federates() && federates(note, owner));
  }

  private boolean federates(NoteSnapshot note, Optional<LocalActor> owner) {
    return owner.filter(actor -> actor.user().id().equals(note.authorId())).isPresent()
        || localActors.byUsername(note.authorUsername()).isPresent();
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

  private static boolean authoredBy(JsonNode object, String actorUri) {
    String uri = idOf(object);
    return uri != null
        && uri.length() <= MAX_URI
        && idsOf(object.get("attributedTo")).contains(actorUri);
  }

  private static Kind reactionKind(String type) {
    if ("Like".equals(type)) {
      return Kind.LIKE;
    }
    return "Announce".equals(type) ? Kind.ANNOUNCE : null;
  }

  private static InboxStep<InboxIntent> act(InboxIntent intent) {
    return InboxStep.next(intent);
  }

  private static InboxStep<InboxIntent> ignored(String reason) {
    return InboxStep.done(InboxOutcome.ignored(reason));
  }

  private static InboxStep<InboxIntent> malformed() {
    return InboxStep.done(InboxOutcome.malformed("fields"));
  }
}
