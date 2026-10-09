package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteRepository extends JpaRepository<NoteEntity, Long> {

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  long countByUserId(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from NoteEntity n where n.id = :id and n.remoteActorId = :actor")
  Optional<NoteEntity> findRemoteForUpdate(@Param("id") Long id, @Param("actor") Long actor);
}
