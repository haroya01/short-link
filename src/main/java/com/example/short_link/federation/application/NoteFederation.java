package com.example.short_link.federation.application;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// Sends a note's Create, Update or Delete to the shared inbox of every follower's server, once per
// server. Cheapest checks first: most authors have no actor or no remote followers, and then the
// note itself is never read.
@Service
@RequiredArgsConstructor
public class NoteFederation {

  private final NoteSnapshotReader notes;
  private final RemoteParents remoteParents;
  private final RemoteAccountFinder finder;
  private final FederationActorService localActors;
  private final FederationSettings settings;
  private final FederationActorRepository actors;
  private final FederationFollowerRepository followers;
  private final NoteDocuments documents;
  private final DeliveryQueue deliveries;
  private final JsonMapper json;
  private final Clock clock;
  private final TransactionTemplate transaction;

  @Transactional
  public void created(Long noteId, Long authorId) {
    deliver(authorId, creation(noteId));
  }

  @Transactional
  public void edited(Long noteId, Long authorId) {
    deliver(authorId, edit(noteId));
  }

  @Transactional
  public void pollEnded(Long noteId, Long authorId) {
    deliver(
        authorId,
        publicId ->
            notes
                .find(noteId)
                .filter(NoteFederation::leaves)
                .map(note -> documents.pollEnded(note, publicId)));
  }

  @Transactional
  public void reposted(Long repostId, Long noteId, Long reposterId) {
    deliver(
        reposterId,
        publicId ->
            notes
                .find(noteId)
                .filter(note -> note.visibility().shareable())
                .flatMap(note -> localActors.byUsername(note.authorUsername()))
                .map(author -> documents.announce(repostId, noteId, publicId, author.publicId())));
  }

  @Transactional
  public void unreposted(Long repostId, Long noteId, Long reposterId) {
    deliver(
        reposterId, publicId -> Optional.of(documents.undoAnnounce(repostId, noteId, publicId)));
  }

  @Transactional
  public void deleted(Long noteId, Long authorId) {
    deliver(authorId, deletion(noteId));
  }

  // A note that answers a note from another server, or names someone there, reaches those servers
  // too, even when the writer has no followers on them; a direct note goes only to them. Handles
  // that resolve to no one are left as text, and a note that reaches no one elsewhere takes the
  // usual path. The people it names are looked up between two transactions: those lookups wait on
  // other servers.
  public void createdElsewhere(Long noteId, Long authorId) {
    transaction
        .execute(status -> stored(noteId, authorId))
        .ifPresent(
            stored -> {
              NoteSnapshot note = stored.note();
              String publicId = stored.actor().publicId();
              Reach reach = stored.reach().naming(named(Mentions.remote(note.body())));
              transaction.executeWithoutResult(
                  status -> {
                    List<String> followerInboxes =
                        note.visibility().followersRead()
                            ? followers.deliveryInboxes(authorId)
                            : List.of();
                    if (reach.isEmpty()) {
                      if (!followerInboxes.isEmpty()) {
                        send(authorId, documents.create(note, publicId), followerInboxes);
                      }
                      return;
                    }
                    send(
                        authorId,
                        documents.create(
                            note, publicId, reach.parent(), reach.addressees(), reach.quoted()),
                        reach.inboxes(followerInboxes));
                  });
            });
  }

  // An edit goes wherever the note went: to the writer's followers, and to the server of the note
  // it answers and of the people it names.
  public void edited(Long noteId, Long authorId, boolean reachesElsewhere) {
    if (!reachesElsewhere) {
      transaction.executeWithoutResult(status -> deliver(authorId, edit(noteId)));
      return;
    }
    transaction
        .execute(status -> stored(noteId, authorId))
        .ifPresent(
            stored -> {
              NoteSnapshot note = stored.note();
              Reach reach = stored.reach().naming(named(Mentions.remote(note.body())));
              transaction.executeWithoutResult(
                  status -> {
                    List<String> inboxes =
                        reach.inboxes(
                            note.visibility().followersRead()
                                ? followers.deliveryInboxes(authorId)
                                : List.of());
                    if (inboxes.isEmpty()) {
                      return;
                    }
                    send(
                        authorId,
                        documents.update(
                            note,
                            stored.actor().publicId(),
                            reach.parent(),
                            reach.addressees(),
                            reach.quoted()),
                        inboxes);
                  });
            });
  }

  // The note is already gone, so what it answered and whom it named come with the call.
  public void deleted(
      Long noteId, Long authorId, Long inReplyToId, List<String> handles, Long quotedNoteId) {
    if (inReplyToId == null && handles.isEmpty() && quotedNoteId == null) {
      transaction.executeWithoutResult(status -> deliver(authorId, deletion(noteId)));
      return;
    }
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    if (actor.isEmpty()) {
      return;
    }
    Reach reach = reach(inReplyToId, handles, quotedNoteId);
    transaction.executeWithoutResult(
        status -> {
          List<String> inboxes = reach.inboxes(followers.deliveryInboxes(authorId));
          if (inboxes.isEmpty()) {
            return;
          }
          send(authorId, documents.delete(noteId, actor.get().publicId()), inboxes);
        });
  }

  private record Reach(
      RemoteParents.Parent parent, List<RemoteActorEntity> named, RemoteParents.Parent quoted) {

    Reach naming(List<RemoteActorEntity> people) {
      return new Reach(parent, people, quoted);
    }

    boolean isEmpty() {
      return parent == null && named.isEmpty() && quoted == null;
    }

    List<NoteDocuments.Addressee> addressees() {
      return named.stream()
          .map(
              person ->
                  new NoteDocuments.Addressee(
                      person.getActorUri(),
                      "@" + person.getUsername() + "@" + person.getDomain(),
                      person.getProfileUrl() == null
                          ? person.getActorUri()
                          : person.getProfileUrl()))
          .toList();
    }

    List<String> inboxes(List<String> followerInboxes) {
      Set<String> inboxes = new LinkedHashSet<>(followerInboxes);
      if (parent != null) {
        inboxes.add(parent.inbox());
      }
      if (quoted != null) {
        inboxes.add(quoted.inbox());
      }
      named.forEach(person -> inboxes.add(person.deliveryInbox()));
      return List.copyOf(inboxes);
    }
  }

  private record Stored(LocalActor actor, NoteSnapshot note, Reach reach) {}

  private Optional<Stored> stored(Long noteId, Long authorId) {
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    Optional<NoteSnapshot> found = actor.isEmpty() ? Optional.empty() : notes.find(noteId);
    if (found.isEmpty() || !found.get().visibility().federates()) {
      return Optional.empty();
    }
    NoteSnapshot note = found.get();
    return Optional.of(
        new Stored(actor.get(), note, storedReach(note.inReplyToId(), quotedElsewhere(note))));
  }

  private Reach reach(Long inReplyToId, List<String> handles, Long quotedNoteId) {
    return storedReach(inReplyToId, quotedNoteId).naming(named(handles));
  }

  private Reach storedReach(Long inReplyToId, Long quotedNoteId) {
    return new Reach(remoteParent(inReplyToId), List.of(), remoteParent(quotedNoteId));
  }

  private RemoteParents.Parent remoteParent(Long noteId) {
    return noteId == null ? null : remoteParents.of(noteId).orElse(null);
  }

  private List<RemoteActorEntity> named(List<String> handles) {
    List<RemoteActorEntity> named = new ArrayList<>();
    for (String handle : handles) {
      finder.find(handle).filter(person -> !person.onSuspendedServer()).ifPresent(named::add);
    }
    return named;
  }

  private static Long quotedElsewhere(NoteSnapshot note) {
    return note.quotedNote() != null && note.quotedNote().remote() && !note.visibility().direct()
        ? note.quotedNote().id()
        : null;
  }

  @Transactional
  public void repostedRemote(Long repostId, Long noteId, Long reposterId, boolean reposted) {
    Optional<LocalActor> actor = localActors.byUserId(reposterId);
    if (actor.isEmpty()) {
      return;
    }
    remoteParents
        .of(noteId)
        .filter(RemoteParents.Parent::shareable)
        .ifPresent(
            note -> {
              List<String> inboxes = new ArrayList<>(followers.deliveryInboxes(reposterId));
              inboxes.add(note.inbox());
              String publicId = actor.get().publicId();
              send(
                  reposterId,
                  reposted
                      ? documents.announceRemote(repostId, note, publicId)
                      : documents.undoAnnounceRemote(repostId, note, publicId),
                  inboxes);
            });
  }

  @Transactional
  public void likedRemote(Long noteId, Long userId, boolean liked) {
    Optional<LocalActor> actor = localActors.byUserId(userId);
    if (actor.isEmpty()) {
      return;
    }
    remoteParents
        .of(noteId)
        .ifPresent(
            note ->
                send(
                    userId,
                    documents.like(noteId, note, actor.get().publicId(), clock.millis(), liked),
                    List.of(note.inbox())));
  }

  private void send(Long signerId, Map<String, Object> body, List<String> inboxes) {
    deliveries.enqueue(signerId, (String) body.get("id"), json.writeValueAsString(body), inboxes);
  }

  private Function<String, Optional<Map<String, Object>>> creation(Long noteId) {
    return publicId ->
        notes
            .find(noteId)
            .filter(NoteFederation::leaves)
            .map(note -> documents.create(note, publicId));
  }

  private Function<String, Optional<Map<String, Object>>> edit(Long noteId) {
    return publicId ->
        notes
            .find(noteId)
            .filter(NoteFederation::leaves)
            .map(note -> documents.update(note, publicId));
  }

  private Function<String, Optional<Map<String, Object>>> deletion(Long noteId) {
    return publicId -> Optional.of(documents.delete(noteId, publicId));
  }

  private static boolean leaves(NoteSnapshot note) {
    return note.visibility().followersRead();
  }

  private void deliver(Long authorId, Function<String, Optional<Map<String, Object>>> activity) {
    Optional<FederationActorEntity> actor = actors.findByUserId(authorId);
    if (actor.isEmpty()) {
      return;
    }
    List<String> inboxes = followers.deliveryInboxes(authorId);
    if (inboxes.isEmpty() || !settings.isEnabled(authorId)) {
      return;
    }
    activity
        .apply(actor.get().getPublicId())
        .ifPresent(
            body ->
                deliveries.enqueue(
                    authorId, (String) body.get("id"), json.writeValueAsString(body), inboxes));
  }
}
