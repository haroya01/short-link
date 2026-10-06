package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteRepostEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNoteRepostRepository extends JpaRepository<NoteRepostEntity, Long> {

  Optional<NoteRepostEntity> findByNoteIdAndUserId(Long noteId, Long userId);
}
