package com.example.short_link.post.note.domain;

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

/** 노트 좋아요 — (note, user) 한 쌍이 곧 상태라 토글은 insert/delete 멱등으로 끝난다. */
@Entity
@Table(
    name = "note_like",
    uniqueConstraints = @UniqueConstraint(columnNames = {"note_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteLikeEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false)
  private Long noteId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  public NoteLikeEntity(Long noteId, Long userId) {
    this.noteId = noteId;
    this.userId = userId;
  }
}
