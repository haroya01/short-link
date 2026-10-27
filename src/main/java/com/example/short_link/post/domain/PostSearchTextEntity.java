package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_search_text")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSearchTextEntity extends BaseTimeEntity {

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
