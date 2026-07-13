package com.example.short_link.common.post;

/**
 * 중립 집행 포트 — abuse(모더레이션) 슬라이스가 post 슬라이스에 직접 의존하지 않고 댓글 soft 삭제를 집행한다. {@code
 * common.post.PostModerationPort} 와 짝을 이루며 슬라이스 그래프를 비순환으로 유지한다(ArchUnit 강제). 구현은 post 슬라이스가 제공하고
 * 호출자 트랜잭션 안에서 실행된다.
 */
public interface CommentModerationPort {

  /**
   * 관리자 권한으로 댓글을 soft 삭제한다(공개 조회에서 숨김·감사 여지 유지). 이미 삭제된 댓글은 무연산, 없는 댓글은 예외. {@code adminUserId} 는
   * 감사 로그용.
   */
  void softDelete(Long adminUserId, Long commentId);
}
