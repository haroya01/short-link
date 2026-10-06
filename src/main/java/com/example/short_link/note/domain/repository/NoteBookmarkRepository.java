package com.example.short_link.note.domain.repository;

import java.util.List;

public interface NoteBookmarkRepository {

  void addIfAbsent(Long noteId, Long userId);

  void delete(Long noteId, Long userId);

  List<Long> recentNoteIdsByUser(Long userId, int offset, int limit);
}
