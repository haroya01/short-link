package com.example.short_link.common.post;

/**
 * 중립 집행 포트 — abuse(모더레이션) 슬라이스가 post 슬라이스에 직접 의존하지 않고 글 게시취소를 집행한다. {@code
 * common.post.PublishedPostCountReader} 와 같은 방식으로 슬라이스 그래프를 비순환으로 유지한다(ArchUnit 강제). 구현은 post 슬라이스가
 * 제공하며 호출자 트랜잭션 안에서 실행된다 — 신고 처리(resolve)와 원자적으로 묶기 위함.
 */
public interface PostModerationPort {

  /**
   * 관리자 권한으로 글을 게시취소(UNPUBLISHED)한다. 이미 게시취소된 글은 무연산(idempotent), 없는 글은 예외. {@code adminUserId} 는
   * 감사 로그용.
   */
  void unpublish(Long adminUserId, Long postId);
}
