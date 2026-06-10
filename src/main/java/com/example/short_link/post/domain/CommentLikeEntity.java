package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One user's like on a comment. (comment_id, user_id) is unique — a user likes a comment once. */
@Entity
@Table(
    name = "comment_like",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_comment_like_comment_user",
            columnNames = {"comment_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentLikeEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "comment_id", nullable = false)
  private Long commentId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  public CommentLikeEntity(Long commentId, Long userId) {
    this.commentId = commentId;
    this.userId = userId;
  }
}
