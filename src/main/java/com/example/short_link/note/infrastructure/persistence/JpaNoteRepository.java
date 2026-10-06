package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteRepository extends JpaRepository<NoteEntity, Long> {

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  @Query(
      "select n.inReplyToId, count(n) from NoteEntity n where n.inReplyToId in :ids"
          + " group by n.inReplyToId")
  List<Object[]> replyCounts(@Param("ids") Collection<Long> ids);
}
