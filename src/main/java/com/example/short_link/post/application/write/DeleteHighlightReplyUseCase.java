package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delete a highlight reply — permitted to the reply's author or the highlight's post owner. */
@Service
@RequiredArgsConstructor
public class DeleteHighlightReplyUseCase {

  private final PostHighlightReplyRepository replyRepository;
  private final PostHighlightRepository highlightRepository;
  private final PostRepository postRepository;

  @Transactional
  public void execute(DeleteHighlightReplyCommand cmd) {
    PostHighlightReplyEntity reply =
        replyRepository
            .findById(cmd.replyId())
            .orElseThrow(
                () -> new PostException(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND, cmd.replyId()));

    if (!reply.isOwnedBy(cmd.userId()) && !isPostOwner(reply.getHighlightId(), cmd.userId())) {
      throw new PostException(PostErrorCode.HIGHLIGHT_REPLY_PERMISSION_DENIED)
          .with("replyId", cmd.replyId());
    }

    replyRepository.delete(reply);
  }

  private boolean isPostOwner(Long highlightId, Long userId) {
    return highlightRepository
        .findById(highlightId)
        .flatMap(h -> postRepository.findById(h.getPostId()))
        .map(p -> p.isOwnedBy(userId))
        .orElse(false);
  }
}
