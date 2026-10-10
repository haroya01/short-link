package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.HighlightReplyModerationPort;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class HighlightReplyModerationAdapter implements HighlightReplyModerationPort {

  private final PostHighlightReplyRepository replyRepository;
  private final Clock clock;

  @Override
  @Transactional
  public void softDelete(Long adminUserId, Long replyId) {
    log.info("admin highlight reply take-down: adminUserId={}, replyId={}", adminUserId, replyId);
    PostHighlightReplyEntity reply =
        replyRepository
            .findById(replyId)
            .orElseThrow(() -> new PostException(PostErrorCode.HIGHLIGHT_REPLY_NOT_FOUND, replyId));
    if (reply.isDeleted()) {
      return;
    }
    reply.softDelete(clock.instant());
    replyRepository.save(reply);
  }
}
