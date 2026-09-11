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

/** 답글은 최상위 댓글만 참조한다. 작성자·글 소유자의 삭제는 물리 삭제, 관리자 삭제는 감사·복구를 위해 deletedAt으로 숨긴다. */
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

  public boolean acceptsReplyOn(Long postId) {
    return this.postId.equals(postId) && !isReply() && !isDeleted();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    if (deletedAt == null) {
      this.deletedAt = Instant.now();
    }
  }
}
