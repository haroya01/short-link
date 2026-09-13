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
 * 검색 평문은 피드·상세의 PostEntity 조회에 큰 본문이 딸려오지 않도록 별도 테이블에 저장한다. 검색 쿼리만 JOIN하여 FULLTEXT(ngram) 인덱스를
 * 사용한다.
 */
@Entity
@Table(name = "post_search_text")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSearchTextEntity extends BaseTimeEntity {

  /** posts.id를 공유 PK로 사용하며 글 삭제 시 ON DELETE CASCADE로 함께 삭제된다. */
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
