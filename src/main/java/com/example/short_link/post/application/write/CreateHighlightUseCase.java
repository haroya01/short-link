package com.example.short_link.post.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateHighlightUseCase {

  private final PostInteractionAccess access;
  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final CollectionConnectionCleaner connectionCleaner;

  @Transactional
  public HighlightRef execute(CreateHighlightCommand cmd) {
    PostEntity post = access.requireInteractablePost(cmd.userId(), cmd.postId());

    String quote =
        cmd.quote().length() > PostHighlightEntity.MAX_QUOTE
            ? cmd.quote().substring(0, PostHighlightEntity.MAX_QUOTE)
            : cmd.quote();

    String note = normalizeNote(cmd.note());

    PostHighlightEntity saved =
        highlightRepository.save(
            new PostHighlightEntity(
                post.getId(),
                cmd.userId(),
                cmd.blockOrder(),
                cmd.endBlockOrder(),
                cmd.startOffset(),
                cmd.endOffset(),
                quote,
                note));

    return new HighlightRef(
        saved.getId(),
        saved.getBlockOrder(),
        saved.getEndBlockOrder(),
        saved.getStartOffset(),
        saved.getEndOffset(),
        saved.getQuote(),
        saved.getCreatedAt(),
        saved.getNote());
  }

  @Transactional
  public void delete(Long userId, Long highlightId) {
    PostHighlightEntity highlight =
        highlightRepository
            .findById(highlightId)
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_NOT_FOUND, highlightId));
    if (!highlight.getUserId().equals(userId)) {
      throw new PostException(PostErrorCode.HIGHLIGHT_PERMISSION_DENIED);
    }
    // DB의 FK cascade 지원에 의존하지 않고 답글도 명시적으로 삭제한다.
    replyRepository.deleteAllByHighlightId(highlight.getId());
    // 컬렉션 연결에는 FK가 없으므로 직접 제거해야 한다.
    connectionCleaner.purgeForHighlights(List.of(highlight.getId()));
    highlightRepository.delete(highlight);
  }

  private static String normalizeNote(String note) {
    if (note == null) return null;
    String trimmed = note.strip();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > PostHighlightEntity.MAX_NOTE
        ? trimmed.substring(0, PostHighlightEntity.MAX_NOTE)
        : trimmed;
  }
}
