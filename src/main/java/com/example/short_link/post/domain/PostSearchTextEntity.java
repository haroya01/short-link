package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 글의 파생 검색 평문(제목·요약·태그·본문 블록을 자연어로 펼친 것)을 담는 1:1 곁 테이블. posts 본문에서 떼어낸 이유는 순전히 읽기 성능이다 —
 * search_text 는 FULLTEXT 인덱스만 훑는 파생 캐시일 뿐 응답에 실리지 않는데, posts 컬럼으로 두면 피드·상세 같은 뜨거운 읽기 경로가 PostEntity
 * 를 로드할 때마다 최대 수십 KB 본문 평문을 함께 끌어온다. 별도 테이블로 옮기면 posts 로드는 이 컬럼을 아예 만지지 않고, 검색만 필요할 때 JOIN 으로 붙는다.
 *
 * <p>키는 post_id 그 자체(공유 PK). FULLTEXT(ngram) 인덱스는 이 테이블의 search_text 위에 있고, 검색 쿼리가 MATCH 로 훑는다. 쓰기
 * 경로는 {@link com.example.short_link.post.application.write.PostSearchTextUpdater} 한 곳에서 upsert 한다.
 */
@Entity
@Table(name = "post_search_text")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSearchTextEntity extends BaseTimeEntity {

  /** 공유 PK — posts.id 를 그대로 키로 쓴다(1:1). FK(ON DELETE CASCADE)로 글이 지워지면 이 행도 자동 정리된다. */
  @Id
  @Column(name = "post_id")
  private Long postId;

  @Column(name = "search_text", columnDefinition = "TEXT")
  private String searchText;

  public PostSearchTextEntity(Long postId, String searchText) {
    this.postId = postId;
    this.searchText = searchText;
  }

  public void updateSearchText(String searchText) {
    this.searchText = searchText;
  }
}
