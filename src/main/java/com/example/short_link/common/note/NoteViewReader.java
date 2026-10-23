package com.example.short_link.common.note;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// The note slice implements this so federation can answer with a note exactly as the note lists
// render it to that viewer, without importing that slice.
public interface NoteViewReader {

  Optional<Object> view(Long noteId, Long viewerId);

  record Listed(Long id, Object view, String uri) {}

  List<Listed> received(Long viewerId, Long beforeId, int limit);

  List<Listed> latest(Long viewerId, Long beforeId, int limit);

  record Pooled(
      Long id,
      Long remoteActorId,
      String domain,
      String language,
      Instant createdAt,
      String uri,
      List<String> tags,
      Instant receivedAt) {}

  List<Pooled> remotePool(
      Long viewerId, Instant since, Instant until, int limit, boolean withFollowed);

  // In the order given, leaving out a note that is gone.
  List<Listed> listed(Long viewerId, List<Long> noteIds);
}
