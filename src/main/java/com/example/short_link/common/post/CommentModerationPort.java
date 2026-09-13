package com.example.short_link.common.post;

/** post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 댓글 삭제를 집행한다. */
public interface CommentModerationPort {

  /**
   * 관리자 권한으로 댓글을 soft 삭제한다(공개 조회에서 숨김·감사 여지 유지). 이미 삭제된 댓글은 무연산, 없는 댓글은 예외. {@code adminUserId} 는
   * 감사 로그용.
   */
  void softDelete(Long adminUserId, Long commentId);
}
