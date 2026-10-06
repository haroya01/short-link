package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteLinkPreviewEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNoteLinkPreviewRepository extends JpaRepository<NoteLinkPreviewEntity, Long> {

  List<NoteLinkPreviewEntity> findByNoteIdIn(Collection<Long> noteIds);
}
