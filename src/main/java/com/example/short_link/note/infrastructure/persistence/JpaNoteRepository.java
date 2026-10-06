package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNoteRepository extends JpaRepository<NoteEntity, Long> {

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  long countByUserId(Long userId);
}
