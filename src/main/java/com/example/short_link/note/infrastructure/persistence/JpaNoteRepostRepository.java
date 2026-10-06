package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteRepostEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteRepostRepository extends JpaRepository<NoteRepostEntity, Long> {

  Optional<NoteRepostEntity> findByNoteIdAndUserId(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  @Query(
      "select r.noteId from NoteRepostEntity r where r.userId = :userId and r.noteId in :noteIds")
  List<Long> repostedNoteIds(
      @Param("userId") Long userId, @Param("noteIds") Collection<Long> noteIds);

  @Query(
      "select r.noteId, count(r) from NoteRepostEntity r where r.noteId in :noteIds"
          + " group by r.noteId")
  List<Object[]> counts(@Param("noteIds") Collection<Long> noteIds);
}
