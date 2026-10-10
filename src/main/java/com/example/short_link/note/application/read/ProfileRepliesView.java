package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import java.util.List;

// replyingTo is null when the parent is gone, never reached this server, or is not for this viewer
// to read or hear; note.inReplyToId still says the note answered something.
public record ProfileRepliesView(List<Item> items, int page, boolean hasNext) {

  public record Item(NoteView note, ReplyContext replyingTo) {}

  // The excerpt stays out behind a content warning, as the parent's body would.
  public record ReplyContext(Long id, NoteAuthor author, String excerpt, String contentWarning) {}
}
