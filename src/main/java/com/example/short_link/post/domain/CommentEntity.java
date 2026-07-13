package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A comment on a published post. One level of threading: a reply sets {@code parentId} to a
 * top-level comment. Anyone can read; only authenticated users create. Deletion is the comment
 * author or the post owner (physical), or an admin moderation take-down (soft — {@code deletedAt}
 * hides it from public reads while keeping an audit/recovery trail).
 */
@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentEntity extends BaseTimeEntity {

  public static final int MAX_BODY = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** Null = top-level comment. Otherwise the id of the top-level comment this replies to. */
  @Column(name = "parent_id")
  private Long parentId;

  @Column(nullable = false, length = MAX_BODY)
  private String body;

  /** 관리자 모더레이션 soft 삭제 시각. null 이면 살아있는 댓글. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  public CommentEntity(Long postId, Long userId, Long parentId, String body) {
    this.postId = postId;
    this.userId = userId;
    this.parentId = parentId;
    this.body = body;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public boolean isReply() {
    return parentId != null;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** 관리자 모더레이션 soft 삭제 — 공개 조회에서 숨긴다. 이미 삭제됐으면 무연산. */
  public void softDelete() {
    if (deletedAt == null) {
      this.deletedAt = Instant.now();
    }
  }
}
