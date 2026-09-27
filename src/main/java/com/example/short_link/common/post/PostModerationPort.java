package com.example.short_link.common.post;

// post 슬라이스가 구현한다. 순환 의존 없이 신고 처리와 같은 트랜잭션에서 게시취소를 집행한다.
public interface PostModerationPort {

  void unpublish(Long adminUserId, Long postId);
}
