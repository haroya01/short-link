package com.example.short_link.post.note.application.write;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.application.read.NoteRow;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteLikeEntity;
import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteCommandService {

  private final NoteRepository notes;
  private final NoteLikeRepository likes;

  /** 쓰는 즉시 공개 — 발행 상태 기계 없음. 응답은 피드와 같은 행(작성자 포함). */
  @Transactional
  public NoteRow create(Long userId, String rawBody) {
    String body = rawBody == null ? "" : rawBody.trim();
    if (body.isEmpty()) {
      throw new PostException(PostErrorCode.NOTE_BODY_REQUIRED);
    }
    if (body.length() > NoteEntity.MAX_BODY_LENGTH) {
      throw new PostException(PostErrorCode.NOTE_BODY_TOO_LONG);
    }
    NoteEntity saved = notes.save(new NoteEntity(userId, body));
    return notes
        .findRowById(saved.getId())
        .orElseThrow(() -> new PostException(PostErrorCode.NOTE_NOT_FOUND, saved.getId()));
  }

  /** 내 노트만 — hard delete(좋아요 행까지 같이). */
  @Transactional
  public void delete(Long userId, Long noteId) {
    NoteEntity note =
        notes
            .findById(noteId)
            .orElseThrow(() -> new PostException(PostErrorCode.NOTE_NOT_FOUND, noteId));
    if (!note.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.NOTE_PERMISSION_DENIED);
    }
    likes.deleteAllByNoteId(noteId);
    notes.delete(note);
  }

  /** 멱등 토글 — (note,user) 유니크가 곧 상태. 동시 PUT 의 중복 insert 는 무해하게 흡수. */
  @Transactional
  public LikeStatus setLike(Long userId, Long noteId, boolean on) {
    notes
        .findById(noteId)
        .orElseThrow(() -> new PostException(PostErrorCode.NOTE_NOT_FOUND, noteId));
    if (on) {
      if (!likes.exists(noteId, userId)) {
        try {
          likes.save(new NoteLikeEntity(noteId, userId));
        } catch (DataIntegrityViolationException ignored) {
          // 동시 요청이 먼저 넣음 — 멱등이므로 그대로 진행.
        }
      }
    } else {
      likes.delete(noteId, userId);
    }
    return new LikeStatus(on, likes.countByNoteId(noteId));
  }

  public record LikeStatus(boolean liked, long likeCount) {}
}
