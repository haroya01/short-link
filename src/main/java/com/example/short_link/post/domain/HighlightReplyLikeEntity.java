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

@Entity
@Table(
    name = "highlight_reply_like",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_highlight_reply_like_reply_user",
            columnNames = {"highlight_reply_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HighlightReplyLikeEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "highlight_reply_id", nullable = false)
  private Long highlightReplyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;
}
