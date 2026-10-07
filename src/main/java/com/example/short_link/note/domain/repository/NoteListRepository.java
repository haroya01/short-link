package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteListEntity;
import com.example.short_link.note.domain.NoteListSummary;
import java.util.List;
import java.util.Optional;

public interface NoteListRepository {

  List<NoteListSummary> summaries(Long userId);

  Optional<NoteListEntity> owned(Long listId, Long userId);

  long countLists(Long userId);

  NoteListEntity save(NoteListEntity list);

  void delete(NoteListEntity list);

  List<Long> memberIds(Long listId);

  long countMembers(Long listId);

  boolean addMember(Long listId, Long memberId);

  void removeMember(Long listId, Long memberId);

  List<Long> listsContaining(Long userId, Long memberId);

  List<NoteEntity> feed(Long listId, Long viewerId, int offset, int limit);
}
