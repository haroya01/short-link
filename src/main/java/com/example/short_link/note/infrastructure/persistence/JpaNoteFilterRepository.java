package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteFilterEntity;
import com.example.short_link.note.domain.repository.NoteFilterRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteFilterRepository
    extends JpaRepository<NoteFilterEntity, Long>, NoteFilterRepository {

  @Override
  @Query(
      "select f from NoteFilterEntity f where f.userId = :userId"
          + " and (f.expiresAt is null or f.expiresAt > :now) order by f.id desc")
  List<NoteFilterEntity> live(@Param("userId") Long userId, @Param("now") Instant now);
}
