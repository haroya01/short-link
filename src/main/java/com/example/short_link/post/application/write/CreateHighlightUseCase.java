package com.example.short_link.post.application.write;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.PostHighlightedEvent;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateHighlightUseCase {

  private final PostInteractionAccess access;
  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final CollectionConnectionCleaner connectionCleaner;
  private final ApplicationEventPublisher events;

  @Transactional
  public HighlightRef execute(CreateHighlightCommand cmd, PostViewer reader) {
    PostEntity post = access.requireInteractablePost(reader, cmd.postId());

    String quote =
        cmd.quote().length() > PostHighlightEntity.MAX_QUOTE
            ? cmd.quote().substring(0, PostHighlightEntity.MAX_QUOTE)
            : cmd.quote();

    String note = normalizeNote(cmd.note());

    PostHighlightEntity highlight =
        new PostHighlightEntity(
            post.getId(),
            cmd.userId(),
            cmd.blockOrder(),
            cmd.endBlockOrder(),
            cmd.startOffset(),
            cmd.endOffset(),
            quote,
            note);
    highlight.saidIn(post.getRoomId());
    PostHighlightEntity saved = highlightRepository.save(highlight);
    if (!post.getUserId().equals(cmd.userId())) {
      events.publishEvent(
          new PostHighlightedEvent(
              post.getUserId(),
              cmd.userId(),
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              saved.getId()));
    }

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
