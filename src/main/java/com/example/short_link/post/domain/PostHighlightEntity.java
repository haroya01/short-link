package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Snapshots the selected quote so post edits cannot erase it. Anchors use block order, matching
 * client rendering, plus character offsets. A single-block span has equal {@code blockOrder} and
 * {@code endBlockOrder}.
 */
@Entity
@Table(name = "post_highlight")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostHighlightEntity extends BaseCreatedEntity {

  public static final int MAX_QUOTE = 1000;
  public static final int MAX_NOTE = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "block_order", nullable = false)
  private Integer blockOrder;

  @Column(name = "end_block_order")
  private Integer endBlockOrder;

  @Column(name = "start_offset", nullable = false)
  private Integer startOffset;

  @Column(name = "end_offset", nullable = false)
  private Integer endOffset;

  @Column(nullable = false, length = MAX_QUOTE)
  private String quote;

  @Column(name = "note", length = MAX_NOTE)
  private String note;

  public PostHighlightEntity(
      Long postId,
      Long userId,
      Integer blockOrder,
      Integer endBlockOrder,
      Integer startOffset,
      Integer endOffset,
      String quote,
      String note) {
    this.postId = postId;
    this.userId = userId;
    this.blockOrder = blockOrder;
    this.endBlockOrder = endBlockOrder == null ? blockOrder : endBlockOrder;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.quote = quote;
    this.note = note;
  }
}
