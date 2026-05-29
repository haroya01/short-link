package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
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
 * A comment on a published post. One level of threading: a reply sets {@code parentId} to a
 * top-level comment. Anyone can read; only authenticated users create. Deletion is the comment
 * author or the post owner.
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
}
