package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.idsOf;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.inbox.EarlyDeletes;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class RemoteNoteIntake {

  private static final int MAX_URI = 512;

  private final RemoteNoteParser parser;
  private final RemoteNotes remoteNotes;
  private final FederationActorRepository actorRows;
  private final FederationFollowers followers;
  private final NoteSnapshotReader notes;
  private final RemoteActorResolver resolver;
  private final EarlyDeletes earlyDeletes;
  private final FederationUrls urls;
  private final RemoteDocuments documents;

  public Optional<Long> keep(
      RemoteActorEntity actor, RemoteNoteParser.Parsed note, NoteSnapshot parent) {
    return keep(actor, note, parent, true);
  }

  private Optional<Long> keep(
      RemoteActorEntity actor,
      RemoteNoteParser.Parsed parsed,
      NoteSnapshot parent,
      boolean follow) {
    RemoteNoteParser.Parsed note = quoted(parsed, follow);
    Optional<Long> kept = store(actor, note, parent, true);
    kept.ifPresent(
        noteId ->
            remoteNotes.preview(
                noteId, note.body(), !note.media().isEmpty() || note.shape().poll() != null, null));
    return kept;
  }

  // A note from the account's own outbox: written by it, public or unlisted, not a reply, and told
  // to no one, since it may be years old.
  public boolean fromHistory(RemoteActorEntity actor, JsonNode object) {
    String uri = idOf(object);
    if (uri == null
        || uri.length() > MAX_URI
        || RemoteNoteParser.kindOf(object).isEmpty()
        || !actor.getDomain().equals(RemoteActorParser.host(uri))
        || !idsOf(object.get("attributedTo")).contains(actor.getActorUri())
        || idOf(object.get("inReplyTo")) != null
        || earlyDeletes.remembered(actor.getId(), uri)) {
      return false;
    }
    RemoteNoteParser.Parsed note = quoted(parser.parse(object, object), false);
    return note.visibility().shareable() && store(actor, note, null, false).isPresent();
  }

  public Optional<Long> relayed(RemoteActorEntity author, RemoteNoteParser.Parsed note) {
    if (earlyDeletes.remembered(author.getId(), note.uri())) {
      return Optional.empty();
    }
    return store(author, quoted(note), null, false);
  }

  // What a quote shows: the quoted post kept beside it and linked when its author's server
  // authorized the quote (or it is a member's public note, which anyone may quote here), else the
  // quoted address at the end of the text. The quoted post's own quote is not followed.
  public RemoteNoteParser.Parsed quoted(RemoteNoteParser.Parsed note) {
    return quoted(note, true);
  }

  private RemoteNoteParser.Parsed quoted(RemoteNoteParser.Parsed note, boolean follow) {
    RemoteNoteParser.Quote quote = note.quote();
    if (quote == null) {
      return note;
    }
    Optional<Long> local = urls.noteIdOf(quote.uri());
    if (local.isPresent()) {
      return notes
          .find(local.get())
          .filter(found -> found.visibility().shareable())
          .map(found -> note.quoting(note.body(), found.id()))
          .orElseGet(() -> linked(note));
    }
    if (!follow || (!quote.accepted() && quote.authorization() == null)) {
      return linked(note);
    }
    if (!quote.accepted() && !authorized(note.uri(), quote)) {
      return linked(note);
    }
    Optional<Long> kept = remoteNotes.kept(quote.uri());
    if (kept.isPresent()) {
      return note.quoting(note.body(), kept.get());
    }
    JsonNode document = document(quote.uri());
    Optional<Long> stored =
        document == null ? Optional.empty() : fetched(document, URI.create(quote.uri()), false);
    if (stored.isEmpty()) {
      return linked(note);
    }
    String url = parser.parse(document, document).url();
    return note.quoting(
        url == null ? note.body() : RemoteNoteParser.withoutReLine(note.body(), List.of(url)),
        stored.get());
  }

  private static RemoteNoteParser.Parsed linked(RemoteNoteParser.Parsed note) {
    return note.quoting(note.linkedBody(), null);
  }

  // FEP-044f: the quoted author's server answers for the quote at the authorization's address,
  // naming the quoting post and the quoted one.
  private boolean authorized(String quoting, RemoteNoteParser.Quote quote) {
    JsonNode authorization = document(quote.authorization());
    String host = RemoteActorParser.host(quote.uri());
    return authorization != null
        && host != null
        && "QuoteAuthorization".equals(ActivityStreams.text(authorization.get("type")))
        && quote.authorization().equals(idOf(authorization))
        && host.equals(RemoteActorParser.host(quote.authorization()))
        && host.equals(RemoteActorParser.host(idOf(authorization.get("attributedTo"))))
        && quote.uri().equals(idOf(authorization.get("interactionTarget")))
        && quoting != null
        && quoting.equals(idOf(authorization.get("interactingObject")));
  }

  private JsonNode document(String uri) {
    try {
      return documents.fetch(URI.create(uri), uri);
    } catch (FederationException | IllegalArgumentException failed) {
      return null;
    }
  }

  private Optional<Long> store(
      RemoteActorEntity actor,
      RemoteNoteParser.Parsed note,
      NoteSnapshot parent,
      boolean notifies) {
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
            note.language(),
            note.shape(),
            notifies,
            note.quotedNoteId()));
  }

  public Optional<Long> fetched(JsonNode object, URI from) {
    return fetched(object, from, true);
  }

  private Optional<Long> fetched(JsonNode object, URI from, boolean follow) {
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
    String attributedTo = idsOf(object.get("attributedTo")).stream().findFirst().orElse(null);
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
    if (!note.visibility().shareable()) {
      return Optional.empty();
    }
    NoteSnapshot parent = urls.noteIdOf(note.inReplyToUri()).flatMap(notes::find).orElse(null);
    return keep(author.get(), note, parent, follow).or(() -> remoteNotes.kept(uri));
  }

  // Mastodon's rule for who may like, answer or vote on a note: anyone on a public or unlisted
  // one, an accepted follower or a named account on a followers-only one, a named account on a
  // direct one. A named account is one the note's text mentions, as delivery reached it.
  public boolean mayRead(RemoteActorEntity actor, NoteSnapshot note) {
    boolean named =
        actor.getUsername() != null
            && Mentions.remote(note.body())
                .contains((actor.getUsername() + "@" + actor.getDomain()).toLowerCase(Locale.ROOT));
    return note.visibility()
        .readableBy(
            reader ->
                switch (reader) {
                  case ANYONE -> true;
                  case AUTHOR -> false;
                  case NAMED -> named;
                  case FOLLOWER -> followers.accepts(note.authorId(), actor.getId());
                  case MEMBER -> false;
                });
  }
}
