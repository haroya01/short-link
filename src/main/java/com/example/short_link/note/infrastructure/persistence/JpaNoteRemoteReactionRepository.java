package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.note.domain.NoteRemoteReactionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteRemoteReactionRepository
    extends JpaRepository<NoteRemoteReactionEntity, Long> {

  long countByNoteIdAndKind(Long noteId, Kind kind);

  @Query(
      "select r.noteId, r.kind, count(r) from NoteRemoteReactionEntity r"
          + " where r.noteId in :noteIds group by r.noteId, r.kind")
  List<Object[]> counts(@Param("noteIds") Collection<Long> noteIds);
}
