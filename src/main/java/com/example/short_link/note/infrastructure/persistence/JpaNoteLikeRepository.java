package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteLikeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteLikeRepository extends JpaRepository<NoteLikeEntity, Long> {

  boolean existsByNoteIdAndUserId(Long noteId, Long userId);

  Optional<NoteLikeEntity> findByNoteIdAndUserId(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  @Modifying
  @Query("delete from NoteLikeEntity l where l.noteId = :noteId")
  int deleteAllByNoteId(@Param("noteId") Long noteId);

  @Query("select l.noteId from NoteLikeEntity l where l.userId = :userId and l.noteId in :noteIds")
  List<Long> likedNoteIds(@Param("userId") Long userId, @Param("noteIds") Collection<Long> noteIds);

  @Query(
      "select l.noteId, count(l) from NoteLikeEntity l where l.noteId in :noteIds"
          + " group by l.noteId")
  List<Object[]> counts(@Param("noteIds") Collection<Long> noteIds);
}
