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
   * Type-specific payload. PARAGRAPH/H1/H2/H3/QUOTE: plain text. IMAGE: JSON {url, alt, caption,
   * key}. CTA_REF: JSON {ctaId} pointing to the CTA library entity. LIST_BULLET/LIST_NUMBERED: JSON
   * array of items. EMBED: JSON {provider, url, html}. DIVIDER: null.
   */
  // V114: TEXT(65,535바이트)는 블록당 100,000자 가드보다 작아 긴 블록 저장이 컬럼에서 터졌다.
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
