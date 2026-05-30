package com.example.short_link.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per public post view — the time-sliced source of truth behind the "trending" sort.
 * posts.view_count stays the denormalized lifetime counter shown on cards; this log lets the feed
 * rank by views inside a rolling window, so "trending" means recent traction rather than all-time
 * totals. Anonymous and un-deduped, mirroring the view_count counter's v0 semantics.
 */
@Entity
@Table(name = "post_view_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false, updatable = false)
  private Long postId;

  @Column(name = "viewed_at", nullable = false, updatable = false)
  private Instant viewedAt;

  public PostViewEventEntity(Long postId, Instant viewedAt) {
    this.postId = postId;
    this.viewedAt = viewedAt;
  }

  @PrePersist
  void prePersist() {
    if (this.viewedAt == null) {
      this.viewedAt = Instant.now();
    }
  }
}
