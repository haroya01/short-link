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
 * A reader's highlight on a published post — a text span anchored by {@code blockOrder} + character
 * offsets, with the selected {@code quote} snapshotted so later post edits don't stale it. Public +
 * attributed (Medium-style social highlights): anyone reading the post sees who highlighted what.
 * The block is referenced by ORDER, not PK, because clients render blocks by order. A highlight may
 * span blocks: it runs from ({@code blockOrder}, {@code startOffset}) to ({@code endBlockOrder},
 * {@code endOffset}); for a single-block highlight {@code endBlockOrder == blockOrder}.
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

  /** Last block the highlight reaches; equals {@code blockOrder} for a single-block highlight. */
  @Column(name = "end_block_order")
  private Integer endBlockOrder;

  @Column(name = "start_offset", nullable = false)
  private Integer startOffset;

  @Column(name = "end_offset", nullable = false)
  private Integer endOffset;

  @Column(nullable = false, length = MAX_QUOTE)
  private String quote;

  /** Optional public margin note — a curator's attributed comment shown alongside the highlight. */
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
    // 단일 블록 하이라이트는 endBlockOrder 가 비어 들어올 수 있다. 항상 채워 두어 읽기 시 null 이 없게 한다.
    this.endBlockOrder = endBlockOrder == null ? blockOrder : endBlockOrder;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.quote = quote;
    this.note = note;
  }
}
