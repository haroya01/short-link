package com.example.short_link.post.domain.repository;

/**
 * 파생 검색 평문(post_search_text) 곁 테이블의 포트. 쓰기 경로는 글의 제목·요약·태그·본문 중 무엇이든 바뀔 때 upsert 로 한 행을 최신화하고, 검색
 * 쿼리는 이 테이블의 FULLTEXT 인덱스를 JOIN 으로 훑는다(포트가 아니라 {@code posts} 네이티브 쿼리에서 직접). 곁 행 삭제는 FK(ON DELETE
 * CASCADE)가 글 삭제 시 자동 처리하므로 별도 연산을 두지 않는다.
 */
public interface PostSearchTextRepository {

  /**
   * 글 하나의 검색 평문을 한 행으로 심는다 — 있으면 갱신, 없으면 삽입. posts.id 를 공유 PK 로 쓰므로 한 글에 한 행뿐이다. 발행/편집은 드문 쓰기라 단건
   * upsert 로 충분하다.
   */
  void upsert(Long postId, String searchText);
}
