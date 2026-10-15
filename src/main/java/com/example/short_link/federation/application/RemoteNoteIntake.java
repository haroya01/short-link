package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.idOf;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Visibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.inbox.EarlyDeletes;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class RemoteNoteIntake {

  private static final int MAX_URI = 512;
  private static final Set<String> SHAREABLE = Set.of("public", "unlisted");

  private final RemoteNoteParser parser;
  private final RemoteNotes remoteNotes;
  private final FederationActorRepository actorRows;
  private final FederationFollowers followers;
  private final NoteSnapshotReader notes;
  private final RemoteActorResolver resolver;
  private final EarlyDeletes earlyDeletes;
  private final FederationUrls urls;

  public Optional<Long> keep(
      RemoteActorEntity actor, RemoteNoteParser.Parsed note, NoteSnapshot parent) {
    boolean answers = parent == null || mayRead(actor, parent);
    List<Long> addressed =
        actorRows.findByPublicIds(note.addressedPublicIds()).stream()
            .map(FederationActorEntity::getUserId)
            .toList();
    return remoteNotes.receive(
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
            note.language()));
  }

  public Optional<Long> fetched(JsonNode object, URI from) {
    String uri = idOf(object);
    if (uri == null || uri.length() > MAX_URI) {
      return Optional.empty();
    }
    String host = RemoteActorParser.host(uri);
    if (host == null || !host.equals(RemoteActorParser.host(from.toString()))) {
      return Optional.empty();
    }
    Optional<Long> kept = remoteNotes.kept(uri);
    if (kept.isPresent()) {
      return kept;
    }
    String attributedTo = idOf(object.get("attributedTo"));
    if (attributedTo == null || !host.equals(RemoteActorParser.host(attributedTo))) {
      return Optional.empty();
    }
    Optional<RemoteActorEntity> author =
        resolver
            .byActorUri(attributedTo)
            .filter(actor -> !actor.onSuspendedServer() && host.equals(actor.getDomain()));
    if (author.isEmpty() || earlyDeletes.remembered(author.get().getId(), uri)) {
      return Optional.empty();
    }
    RemoteNoteParser.Parsed note = parser.parse(object, object);
    if (!SHAREABLE.contains(note.visibility())) {
      return Optional.empty();
    }
    NoteSnapshot parent = urls.noteIdOf(note.inReplyToUri()).flatMap(notes::find).orElse(null);
    return keep(author.get(), note, parent).or(() -> remoteNotes.kept(uri));
  }

  // Mastodon's rule for who may like, answer or vote on a note: anyone on a public or unlisted
  // one, an accepted follower or a named account on a followers-only one, a named account on a
  // direct one. A named account is one the note's text mentions, as delivery reached it.
  public boolean mayRead(RemoteActorEntity actor, NoteSnapshot note) {
    if (note.visibility() == Visibility.PUBLIC || note.visibility() == Visibility.UNLISTED) {
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
}
