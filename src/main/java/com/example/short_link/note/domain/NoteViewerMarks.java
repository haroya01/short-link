package com.example.short_link.note.domain;

import java.util.Set;

// What the reader did to a page of notes: liked, reposted, bookmarked, muted the conversation of.
public record NoteViewerMarks(
    Set<Long> liked, Set<Long> reposted, Set<Long> bookmarked, Set<Long> muted) {

  public static final NoteViewerMarks NONE =
      new NoteViewerMarks(Set.of(), Set.of(), Set.of(), Set.of());

  public NoteViewerMarks(Set<Long> liked, Set<Long> reposted, Set<Long> bookmarked) {
    this(liked, reposted, bookmarked, Set.of());
  }
}
