package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.SeenViewer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ForYouSignalReader {

  enum Kind {
    LIKED,
    BOOKMARKED,
    ANSWERED,
    FOLLOWED,
    DWELT
  }

  record Signal(Kind kind, String author, String language, List<String> tags) {}

  Optional<String> locale(long userId);

  // A member's latest likes, bookmarks, answers and notes by accounts they follow, each kind up to
  // perKind since the time, and for any viewer the notes they stayed on at least minDwellMs.
  List<Signal> signals(SeenViewer viewer, Instant since, int perKind, int minDwellMs);

  Set<Long> followed(long userId);
}
