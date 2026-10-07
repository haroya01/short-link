package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteViewerMarks;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  List<NoteEntity> topLevel(int offset, int limit);

  List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit);

  Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds);

  void addRecipients(Long noteId, Collection<Long> userIds);

  List<NoteEntity> direct(Long viewerId, int offset, int limit);

  List<NoteEntity> trending(int offset, int limit);

  List<NoteFeedRow> following(Collection<Long> authorIds, Long viewerId, int offset, int limit);

  List<NoteEntity> replies(Long noteId, int limit);

  Map<Long, NoteStats> stats(Collection<Long> noteIds);

  NoteViewerMarks viewerMarks(Long userId, Collection<Long> noteIds);

  List<NoteEntity> quotesOf(Long noteId, int offset, int limit);

  List<NoteEntity> tagged(String tag, int offset, int limit);

  long countPinned(Long userId);

  void recordVersion(Long noteId, NoteVersion version);

  List<NoteVersion> versions(Long noteId);

  List<Long> pinnedIds(Long userId);

  void tag(Long noteId, List<String> tags);

  void retag(Long noteId, List<String> tags);

  long countByAuthor(Long userId);
}
