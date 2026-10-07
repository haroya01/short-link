package com.example.short_link.federation.application;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Visibility;
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

  @Transactional
  public void created(Long noteId, Long authorId) {
    deliver(
        authorId,
        publicId ->
            notes
                .find(noteId)
                .filter(NoteFederation::leaves)
                .map(note -> documents.create(note, publicId)));
  }

  @Transactional
  public void edited(Long noteId, Long authorId) {
    deliver(
        authorId,
        publicId ->
            notes
                .find(noteId)
                .filter(NoteFederation::leaves)
                .map(note -> documents.update(note, publicId)));
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
                .filter(
                    note ->
                        note.visibility() == Visibility.PUBLIC
                            || note.visibility() == Visibility.UNLISTED)
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
    deliver(authorId, publicId -> Optional.of(documents.delete(noteId, publicId)));
  }

  // A note that answers a note from another server, or names someone there, reaches those servers
  // too, even when the writer has no followers on them; a direct note goes only to them. Handles
  // that resolve to no one are left as text, and a note that reaches no one elsewhere takes the
  // usual path.
  @Transactional
  public void createdElsewhere(Long noteId, Long authorId) {
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    Optional<NoteSnapshot> found = actor.isEmpty() ? Optional.empty() : notes.find(noteId);
    if (found.isEmpty()) {
      return;
    }
    NoteSnapshot note = found.get();
    Reach reach = reach(note.inReplyToId(), Mentions.remote(note.body()));
    if (reach.isEmpty()) {
      created(noteId, authorId);
      return;
    }
    send(
        authorId,
        documents.create(note, actor.get().publicId(), reach.parent(), reach.addressees()),
        reach.inboxes(
            note.visibility() == Visibility.DIRECT
                ? List.of()
                : followers.deliveryInboxes(authorId)));
  }

  // An edit goes wherever the note went: to the writer's followers, and to the server of the note
  // it answers and of the people it names.
  @Transactional
  public void edited(Long noteId, Long authorId, boolean reachesElsewhere) {
    if (!reachesElsewhere) {
      edited(noteId, authorId);
      return;
    }
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    Optional<NoteSnapshot> found = actor.isEmpty() ? Optional.empty() : notes.find(noteId);
    if (found.isEmpty()) {
      return;
    }
    NoteSnapshot note = found.get();
    Reach reach = reach(note.inReplyToId(), Mentions.remote(note.body()));
    List<String> inboxes =
        reach.inboxes(
            note.visibility() == Visibility.DIRECT
                ? List.of()
                : followers.deliveryInboxes(authorId));
    if (inboxes.isEmpty()) {
      return;
    }
    send(
        authorId,
        documents.update(note, actor.get().publicId(), reach.parent(), reach.addressees()),
        inboxes);
  }

  // The note is already gone, so what it answered and whom it named come with the call.
  @Transactional
  public void deleted(Long noteId, Long authorId, Long inReplyToId, List<String> handles) {
    if (inReplyToId == null && handles.isEmpty()) {
      deleted(noteId, authorId);
      return;
    }
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    if (actor.isEmpty()) {
      return;
    }
    List<String> inboxes = reach(inReplyToId, handles).inboxes(followers.deliveryInboxes(authorId));
    if (inboxes.isEmpty()) {
      return;
    }
    send(authorId, documents.delete(noteId, actor.get().publicId()), inboxes);
  }

  private record Reach(RemoteParents.Parent parent, List<RemoteActorEntity> named) {

    boolean isEmpty() {
      return parent == null && named.isEmpty();
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
      named.forEach(person -> inboxes.add(person.deliveryInbox()));
      return List.copyOf(inboxes);
    }
  }

  private Reach reach(Long inReplyToId, List<String> handles) {
    RemoteParents.Parent parent = remoteParents.of(inReplyToId).orElse(null);
    List<RemoteActorEntity> named = new ArrayList<>();
    for (String handle : handles) {
      finder.find(handle).ifPresent(named::add);
    }
    return new Reach(parent, named);
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

  private static boolean leaves(NoteSnapshot note) {
    return note.visibility() != Visibility.DIRECT;
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
