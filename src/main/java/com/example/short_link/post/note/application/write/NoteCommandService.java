package com.example.short_link.post.note.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteCommandService {

  private final NoteRepository notes;
  private final NoteLikeRepository likes;
  private final CollectionConnectionCleaner connectionCleaner;

  /** 생성 즉시 공개하며 별도 발행 상태는 없다. */
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

  /** 소유자만 물리 삭제할 수 있으며 좋아요도 함께 삭제한다. */
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
    // 컬렉션 연결에는 FK가 없으므로 노트 삭제 전에 직접 제거한다.
    connectionCleaner.purgeForNote(noteId);
    notes.delete(note);
  }

  /** 동시 중복 요청은 멱등 처리한다. */
  @Transactional
  public LikeStatus setLike(Long userId, Long noteId, boolean on) {
    notes
        .findById(noteId)
        .orElseThrow(() -> new PostException(PostErrorCode.NOTE_NOT_FOUND, noteId));
    if (on) {
      likes.addIfAbsent(noteId, userId);
    } else {
      likes.delete(noteId, userId);
    }
    return new LikeStatus(on, likes.countByNoteId(noteId));
  }

  public record LikeStatus(boolean liked, long likeCount) {}
}
