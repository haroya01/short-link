package com.example.short_link.note.application.read;

import java.util.List;

// continuation is the author's own parts under the note, in order; replies are everyone else's.
public record NoteThreadView(
    NoteView note, NoteView parent, List<NoteView> replies, List<NoteView> continuation) {

  public NoteThreadView(NoteView note, NoteView parent, List<NoteView> replies) {
    this(note, parent, replies, List.of());
  }
}
