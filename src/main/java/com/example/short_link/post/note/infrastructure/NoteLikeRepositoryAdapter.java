package com.example.short_link.post.note.infrastructure;

import com.example.short_link.post.note.domain.NoteLikeEntity;
import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NoteLikeRepositoryAdapter implements NoteLikeRepository {

  private final JpaNoteLikeRepository jpa;

  @Override
  public void addIfAbsent(Long noteId, Long userId) {
    if (jpa.existsByNoteIdAndUserId(noteId, userId)) return;
    try {
      jpa.save(new NoteLikeEntity(noteId, userId));
    } catch (DataIntegrityViolationException ignored) {
      // 기존 동시 중복 요청 처리 방식. 트랜잭션 경계와 flush 시점은 변경하지 않는다.
    }
  }

  @Override
  public void delete(Long noteId, Long userId) {
    jpa.findByNoteIdAndUserId(noteId, userId).ifPresent(jpa::delete);
  }

  @Override
  public long countByNoteId(Long noteId) {
    return jpa.countByNoteId(noteId);
  }

  @Override
  public List<Long> likedNoteIds(Long userId, Collection<Long> noteIds) {
    return noteIds.isEmpty() ? List.of() : jpa.likedNoteIds(userId, noteIds);
  }

  @Override
  public void deleteAllByNoteId(Long noteId) {
    jpa.deleteAllByNoteId(noteId);
  }
}
