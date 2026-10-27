package com.example.short_link.federation.application;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final NoteAddressing addressing;
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
  // usual path. The people it names are looked up before the transaction opens: those lookups
  // wait on other servers.
  public void createdElsewhere(Long noteId, Long authorId) {
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    Optional<NoteSnapshot> found = actor.isEmpty() ? Optional.empty() : notes.find(noteId);
    if (found.isEmpty() || !found.get().visibility().federates()) {
      return;
    }
    NoteSnapshot note = found.get();
    Addressing elsewhere = addressing.lookedUp(note);
    transaction.executeWithoutResult(
        status -> {
          if (elsewhere.isEmpty()) {
            deliver(authorId, creation(noteId));
            return;
          }
          send(
              authorId,
              documents.create(
                  note,
                  actor.get().publicId(),
                  elsewhere.parent(),
                  elsewhere.addressees(),
                  elsewhere.quoted()),
              elsewhere.inboxes(
                  note.visibility().followersRead()
                      ? followers.deliveryInboxes(authorId)
                      : List.of()));
        });
  }

  // An edit goes wherever the note went: to the writer's followers, and to the server of the note
  // it answers and of the people it names.
  public void edited(Long noteId, Long authorId, boolean reachesElsewhere) {
    if (!reachesElsewhere) {
      transaction.executeWithoutResult(status -> deliver(authorId, edit(noteId)));
      return;
    }
    Optional<LocalActor> actor = localActors.byUserId(authorId);
    Optional<NoteSnapshot> found = actor.isEmpty() ? Optional.empty() : notes.find(noteId);
    if (found.isEmpty() || !found.get().visibility().federates()) {
      return;
    }
    NoteSnapshot note = found.get();
    Addressing elsewhere = addressing.lookedUp(note);
    transaction.executeWithoutResult(
        status -> {
          List<String> inboxes =
              elsewhere.inboxes(
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
                  actor.get().publicId(),
                  elsewhere.parent(),
                  elsewhere.addressees(),
                  elsewhere.quoted()),
              inboxes);
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
    Addressing elsewhere = addressing.lookedUp(inReplyToId, handles, quotedNoteId);
    transaction.executeWithoutResult(
        status -> {
          List<String> inboxes = elsewhere.inboxes(followers.deliveryInboxes(authorId));
          if (inboxes.isEmpty()) {
            return;
          }
          send(authorId, documents.delete(noteId, actor.get().publicId()), inboxes);
        });
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
