package com.example.short_link.federation.application;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// A note received from another server, as our replies, boosts and likes address it: its id, its
// author's actor and the inbox that author's server reads.
@Component
@RequiredArgsConstructor
public class RemoteParents {

  private final RemoteNotes remoteNotes;
  private final RemoteActorRepository remoteActors;

  public record Parent(
      String uri, String actorUri, String handle, String inbox, boolean shareable) {}

  public Optional<Parent> of(Long noteId) {
    if (noteId == null) {
      return Optional.empty();
    }
    return remoteNotes
        .target(noteId)
        .flatMap(
            target ->
                remoteActors.findById(target.remoteActorId()).map(actor -> parent(target, actor)));
  }

  private static Parent parent(RemoteNotes.Target target, RemoteActorEntity actor) {
    String handle =
        "@" + (actor.getUsername() == null ? "" : actor.getUsername()) + "@" + actor.getDomain();
    return new Parent(
        target.uri(), actor.getActorUri(), handle, actor.deliveryInbox(), target.shareable());
  }
}
