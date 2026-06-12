package com.example.short_link.post.note.infrastructure;

import com.example.short_link.post.note.domain.NoteLikeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNoteLikeRepository extends JpaRepository<NoteLikeEntity, Long> {

  boolean existsByNoteIdAndUserId(Long noteId, Long userId);

  Optional<NoteLikeEntity> findByNoteIdAndUserId(Long noteId, Long userId);

  long countByNoteId(Long noteId);

  void deleteAllByNoteId(Long noteId);

  @Query("select l.noteId from NoteLikeEntity l where l.userId = :userId and l.noteId in :noteIds")
  List<Long> likedNoteIds(@Param("userId") Long userId, @Param("noteIds") Collection<Long> noteIds);
}
