package com.example.short_link.note.domain;

import java.util.Set;

// What the reader did to a page of notes: liked, reposted, bookmarked.
public record NoteViewerMarks(Set<Long> liked, Set<Long> reposted, Set<Long> bookmarked) {

  public static final NoteViewerMarks NONE = new NoteViewerMarks(Set.of(), Set.of(), Set.of());
}
