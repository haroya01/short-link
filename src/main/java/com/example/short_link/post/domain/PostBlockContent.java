package com.example.short_link.post.domain;

/** 블록 변환에 필요한 본문만 노출한다. 식별자와 편집 순서는 변환 규칙에 포함하지 않는다. */
public interface PostBlockContent {
  String type();

  String content();
}
