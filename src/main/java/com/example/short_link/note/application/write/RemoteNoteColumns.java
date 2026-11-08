package com.example.short_link.note.application.write;

import com.example.short_link.common.note.RemoteNotes.Media;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Rows keep the remote address until a copy moves it to remote_url; compare by originalUrl().
final class RemoteNoteColumns {

  static final int MAX_TITLE = 255;
  private static final int MAX_CONTENT_TYPE = 32;

  private RemoteNoteColumns() {}

  static List<NoteMediaEntity> mediaRows(Long noteId, List<Media> attached) {
    List<NoteMediaEntity> rows = new ArrayList<>();
    if (attached == null) {
      return rows;
    }
    for (Media image : attached) {
      if (rows.size() == NoteMediaEntity.MAX_PER_NOTE) {
        break;
      }
      rows.add(
          new NoteMediaEntity(
              noteId,
              rows.size(),
              "",
              image.url(),
              contentType(image.contentType()),
              cut(image.altText(), NoteMediaEntity.MAX_ALT_TEXT_LENGTH),
              image.width(),
              image.height()));
    }
    return rows;
  }

  static boolean sameMedia(List<NoteMediaEntity> kept, List<NoteMediaEntity> sent) {
    if (kept.size() != sent.size()) {
      return false;
    }
    for (int i = 0; i < kept.size(); i++) {
      NoteMediaEntity a = kept.get(i);
      NoteMediaEntity b = sent.get(i);
      if (!a.originalUrl().equals(b.getUrl())
          || !a.getContentType().equals(b.getContentType())
          || !Objects.equals(a.getAltText(), b.getAltText())
          || !Objects.equals(a.getWidth(), b.getWidth())
          || !Objects.equals(a.getHeight(), b.getHeight())) {
        return false;
      }
    }
    return true;
  }

  record Marks(String warning, boolean sensitive) {

    static Marks of(String warning, boolean sensitive) {
      String kept = cut(warning, NoteEntity.MAX_WARNING_LENGTH);
      return new Marks(kept, sensitive || kept != null);
    }
  }

  static String cut(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    return stripped.codePointCount(0, stripped.length()) <= max
        ? stripped
        : stripped.substring(0, stripped.offsetByCodePoints(0, max - 1)) + "…";
  }

  private static String contentType(String type) {
    if (type == null || type.isBlank()) {
      return "image/jpeg";
    }
    String stripped = type.strip();
    return stripped.length() <= MAX_CONTENT_TYPE
        ? stripped
        : stripped.substring(0, MAX_CONTENT_TYPE);
  }
}
