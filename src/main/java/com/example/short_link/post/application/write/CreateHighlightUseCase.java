package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create / delete a reader highlight. Highlights only attach to published posts; the quote is
 * clamped to the column cap. Delete is owner-only. (Author notification is a follow-up step — kept
 * out so this lands without touching the notification subsystem.)
 */
@Service
@RequiredArgsConstructor
public class CreateHighlightUseCase {

  private final PostRepository postRepository;
  private final PostHighlightRepository highlightRepository;

  @Transactional
  public HighlightRef execute(CreateHighlightCommand cmd) {
    PostEntity post =
        postRepository
            .findById(cmd.postId())
            .filter(PostEntity::isPublished)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId()));

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
                cmd.startOffset(),
                cmd.endOffset(),
                quote,
                note));

    return new HighlightRef(
        saved.getId(),
        saved.getBlockOrder(),
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
