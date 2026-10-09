package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteMediaRepository extends JpaRepository<NoteMediaEntity, Long> {

  List<NoteMediaEntity> findByNoteIdInOrderByNoteIdAscPositionAsc(Collection<Long> noteIds);

  @Modifying
  @Query("delete from NoteMediaEntity m where m.noteId = :noteId")
  int deleteAllByNoteId(@Param("noteId") Long noteId);
}
