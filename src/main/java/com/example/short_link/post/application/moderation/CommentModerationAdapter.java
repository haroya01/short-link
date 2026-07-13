package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.CommentModerationPort;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * post 슬라이스의 {@link CommentModerationPort} 구현 — abuse 슬라이스의 신고 처리(resolve)가 post 에 직접 의존하지 않고 댓글
 * soft 삭제를 집행하게 한다. 없는 댓글은 404, 이미 삭제됐으면 무연산. 호출자 트랜잭션 안에서 실행된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CommentModerationAdapter implements CommentModerationPort {

  private final CommentRepository commentRepository;

  @Override
  @Transactional
  public void softDelete(Long adminUserId, Long commentId) {
    log.info("admin comment take-down: adminUserId={}, commentId={}", adminUserId, commentId);
    CommentEntity comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_NOT_FOUND, commentId));
    if (comment.isDeleted()) {
      return;
    }
    comment.softDelete();
    commentRepository.save(comment);
  }
}
