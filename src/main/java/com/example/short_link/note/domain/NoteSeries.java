package com.example.short_link.note.domain;

import java.util.List;

// The series a note belongs to, with what a reader can open in it, in order.
public record NoteSeries(Long id, String slug, String title, List<Entry> entries) {

  // A post is reached by its slug, a note by its id.
  public record Entry(String type, Long refId, String slug, String title) {

    public boolean isNote(Long noteId) {
      return "NOTE".equals(type) && refId.equals(noteId);
    }
  }
}
