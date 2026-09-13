package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostBlockEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Enumerated(EnumType.STRING)
  @Column(name = "block_type", nullable = false, length = 16)
  private PostBlockType type;

  /**
   * 텍스트는 원문, IMAGE/CTA_REF/EMBED/CODE는 타입별 JSON, DIVIDER는 null이다. LIST는 마크다운을 사용하며 구형 JSON 문자열 배열도
   * 읽는다.
   */
  // 블록당 100,000자 한도를 수용하려면 TEXT의 65,535바이트보다 큰 컬럼이 필요하다.
  @Column(columnDefinition = "MEDIUMTEXT")
  private String content;

  @Column(name = "block_order", nullable = false)
  private Integer blockOrder;

  public PostBlockEntity(Long postId, PostBlockType type, String content, int blockOrder) {
    this.postId = postId;
    this.type = type;
    this.content = content;
    this.blockOrder = blockOrder;
  }

  public boolean belongsTo(Long postId) {
    return this.postId.equals(postId);
  }

  public void updateContent(String content) {
    this.content = content;
  }

  public void updateBlockOrder(int blockOrder) {
    this.blockOrder = blockOrder;
  }
}
