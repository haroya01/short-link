package com.example.short_link.common.post;

// post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 글 내리기를 집행한다.
public interface PostModerationPort {

  void takeDown(Long adminUserId, Long postId);
}
