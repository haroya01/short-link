package com.example.short_link.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One reader's read of a post (the "reading history"). {@code (user_id, post_id)} is unique — a
 * re-read just moves {@code read_at} forward (the row floats to the top of the history). Unlike the
 * anonymous {@code post_view_event} log, this is the reader's own private record.
 */
@Entity
@Table(
    name = "post_read",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_post_read_user_post",
            columnNames = {"user_id", "post_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReadEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "read_at", nullable = false)
  private Instant readAt;

  public PostReadEntity(Long userId, Long postId, Instant readAt) {
    this.userId = userId;
    this.postId = postId;
    this.readAt = readAt;
  }

  /** Re-read — float this entry back to the top of the history. */
  public void touch(Instant readAt) {
    this.readAt = readAt;
  }
}
