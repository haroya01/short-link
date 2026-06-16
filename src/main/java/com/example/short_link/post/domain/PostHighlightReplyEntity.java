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
 * A reply in the flat thread under a reader highlight. The highlight's {@code note} is the opener;
 * anyone may read, only authenticated users create. Deletion is the reply author or the highlight's
 * post owner. The highlight FK cascades, so highlight (and post / account) removal takes the
 * thread.
 */
@Entity
@Table(name = "highlight_reply")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostHighlightReplyEntity extends BaseTimeEntity {

  public static final int MAX_BODY = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "highlight_id", nullable = false)
  private Long highlightId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = MAX_BODY)
  private String body;

  public PostHighlightReplyEntity(Long highlightId, Long userId, String body) {
    this.highlightId = highlightId;
    this.userId = userId;
    this.body = body;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }
}
