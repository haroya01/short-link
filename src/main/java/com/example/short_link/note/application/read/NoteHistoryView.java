package com.example.short_link.note.application.read;

import java.time.Instant;
import java.util.List;

// Newest first; the first version is the note as it reads now.
public record NoteHistoryView(Long noteId, List<Version> versions) {

  public record Version(String body, String contentWarning, boolean sensitive, Instant at) {}
}
