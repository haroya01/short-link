package com.example.short_link.common.post;

// post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 댓글 삭제를 집행한다.
public interface CommentModerationPort {

  void softDelete(Long adminUserId, Long commentId);
}
