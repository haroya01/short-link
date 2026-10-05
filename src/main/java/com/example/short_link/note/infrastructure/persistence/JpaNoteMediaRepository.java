package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNoteMediaRepository extends JpaRepository<NoteMediaEntity, Long> {

  List<NoteMediaEntity> findByNoteIdInOrderByNoteIdAscPositionAsc(Collection<Long> noteIds);
}
