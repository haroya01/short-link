package com.example.short_link.common.post;

/** post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 게시취소를 집행한다. */
public interface PostModerationPort {

  /**
   * 관리자 권한으로 글을 게시취소(UNPUBLISHED)한다. 이미 게시취소된 글은 무연산(idempotent), 없는 글은 예외. {@code adminUserId} 는
   * 감사 로그용.
   */
  void unpublish(Long adminUserId, Long postId);
}
