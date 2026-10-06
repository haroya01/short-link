package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteStats;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  List<NoteEntity> topLevel(int offset, int limit);

  List<NoteEntity> topLevelByAuthors(Collection<Long> authorIds, int offset, int limit);

  List<NoteEntity> trending(int offset, int limit);

  List<NoteFeedRow> following(Collection<Long> authorIds, Long viewerId, int offset, int limit);

  List<NoteEntity> replies(Long noteId, int limit);

  Map<Long, NoteStats> stats(Collection<Long> noteIds);

  long countByAuthor(Long userId);
}
