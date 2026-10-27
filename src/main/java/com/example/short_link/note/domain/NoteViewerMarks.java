package com.example.short_link.note.domain;

import java.util.Set;

public record NoteViewerMarks(
    Set<Long> liked, Set<Long> reposted, Set<Long> bookmarked, Set<Long> muted) {

  public static final NoteViewerMarks NONE =
      new NoteViewerMarks(Set.of(), Set.of(), Set.of(), Set.of());

  public NoteViewerMarks(Set<Long> liked, Set<Long> reposted, Set<Long> bookmarked) {
    this(liked, reposted, bookmarked, Set.of());
  }
}
