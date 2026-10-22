package com.example.short_link.common.note;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Notes from accounts on other servers. The federation slice verifies and parses them; the note
// slice keeps them beside members' notes, keyed by their ActivityPub id.
public interface RemoteNotes {

  enum Kind {
    NOTE,
    QUESTION,
    ARTICLE,
    PAGE,
    VIDEO
  }

  // Votes are cast and counted on the poll's own server: one count per option, in option order.
  record Poll(
      List<String> options,
      List<Long> votes,
      long voters,
      boolean multiple,
      Instant endsAt,
      boolean closed) {}

  record Shape(Kind kind, String title, String community, Poll poll) {
    public static final Shape NOTE = new Shape(Kind.NOTE, null, null, null);
  }

  // notifies is false for notes fetched from an account's history: nobody hears of those.
  record Received(
      Long remoteActorId,
      String uri,
      String url,
      String body,
      String contentWarning,
      boolean sensitive,
      NoteVisibility visibility,
      Instant publishedAt,
      Long inReplyToLocalId,
      String inReplyToUri,
      Collection<Long> addressedUserIds,
      List<Media> media,
      String language,
      Shape shape,
      boolean notifies) {

    public Received(
        Long remoteActorId,
        String uri,
        String url,
        String body,
        String contentWarning,
        boolean sensitive,
        NoteVisibility visibility,
        Instant publishedAt,
        Long inReplyToLocalId,
        String inReplyToUri,
        Collection<Long> addressedUserIds,
        List<Media> media,
        String language) {
      this(
          remoteActorId,
          uri,
          url,
          body,
          contentWarning,
          sensitive,
          visibility,
          publishedAt,
          inReplyToLocalId,
          inReplyToUri,
          addressedUserIds,
          media,
          language,
          Shape.NOTE,
          true);
    }
  }

  record Media(String url, String altText, String contentType, Integer width, Integer height) {
    public Media(String url, String altText, String contentType) {
      this(url, altText, contentType, null, null);
    }
  }

  record Revision(
      String body,
      String contentWarning,
      boolean sensitive,
      Instant editedAt,
      List<Media> media,
      String language,
      Shape shape) {

    public Revision(
        String body,
        String contentWarning,
        boolean sensitive,
        Instant editedAt,
        List<Media> media,
        String language) {
      this(body, contentWarning, sensitive, editedAt, media, language, Shape.NOTE);
    }
  }

  record Target(String uri, Long remoteActorId, boolean shareable) {}

  record LinkCard(String url, String title, String description, String image) {}

  boolean exists(String uri);

  Optional<Long> kept(String uri);

  Optional<Target> target(Long noteId);

  Optional<Long> receive(Received note);

  boolean revise(Long remoteActorId, Long noteId, Revision revision);

  // The card the note's own server made for it, kept as its link card; without one, the first
  // address in its text is read once the transaction commits, as a member's note's is.
  void preview(Long noteId, String body, boolean hasMedia, LinkCard card);

  boolean retract(Long remoteActorId, String uri);

  // The account's public and unlisted notes that are not replies, counted up to atMost.
  int countListed(Long remoteActorId, int atMost);
}
