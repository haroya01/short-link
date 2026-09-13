package com.example.short_link.post.domain.repository;

/** 검색은 네이티브 쿼리에서 이 테이블의 FULLTEXT 인덱스를 JOIN한다. 글 삭제 시 FK의 ON DELETE CASCADE가 검색 행도 제거한다. */
public interface PostSearchTextRepository {

  void upsert(Long postId, String searchText);
}
