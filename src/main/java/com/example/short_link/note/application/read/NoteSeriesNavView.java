package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteSeries;
import java.util.List;

public record NoteSeriesNavView(
    String slug, String title, int position, int total, ItemLink prev, ItemLink next) {

  // A post is reached by its slug, a note by its id.
  public record ItemLink(String type, String slug, Long noteId, String title) {

    static ItemLink of(NoteSeries.Entry entry) {
      return "POST".equals(entry.type())
          ? new ItemLink(entry.type(), entry.slug(), null, entry.title())
          : new ItemLink(entry.type(), null, entry.refId(), entry.title());
    }
  }

  static NoteSeriesNavView of(NoteSeries series, Long noteId) {
    List<NoteSeries.Entry> entries = series.entries();
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).isNote(noteId)) {
        return new NoteSeriesNavView(
            series.slug(),
            series.title(),
            i + 1,
            entries.size(),
            i > 0 ? ItemLink.of(entries.get(i - 1)) : null,
            i < entries.size() - 1 ? ItemLink.of(entries.get(i + 1)) : null);
      }
    }
    return null;
  }
}
