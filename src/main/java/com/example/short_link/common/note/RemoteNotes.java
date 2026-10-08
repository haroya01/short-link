package com.example.short_link.common.note;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Notes from accounts on other servers. The federation slice verifies and parses them; the note
// slice keeps them beside members' notes, keyed by their ActivityPub id.
public interface RemoteNotes {

  record Received(
      Long remoteActorId,
      String uri,
      String url,
      String body,
      String contentWarning,
      boolean sensitive,
      String visibility,
      Instant publishedAt,
      Long inReplyToLocalId,
      String inReplyToUri,
      Collection<Long> addressedUserIds,
      List<Media> media,
      String language) {}

  record Media(String url, String altText, String contentType, Integer width, Integer height) {
    public Media(String url, String altText, String contentType) {
      this(url, altText, contentType, null, null);
    }
  }

  record Target(String uri, Long remoteActorId, boolean shareable) {}

  boolean exists(String uri);

  Optional<Long> kept(String uri);

  Optional<Target> target(Long noteId);

  Optional<Long> receive(Received note);

  boolean revise(
      Long remoteActorId,
      Long noteId,
      String body,
      String contentWarning,
      boolean sensitive,
      Instant editedAt);

  boolean retract(Long remoteActorId, String uri);
}
