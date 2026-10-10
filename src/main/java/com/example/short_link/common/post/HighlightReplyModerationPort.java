package com.example.short_link.common.post;

// post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 하이라이트 답글을 내린다.
public interface HighlightReplyModerationPort {

  void softDelete(Long adminUserId, Long replyId);
}
