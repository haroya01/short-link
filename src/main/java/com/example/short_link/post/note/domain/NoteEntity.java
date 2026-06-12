package com.example.short_link.post.note.domain;

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
 * 짧은 글(노트) — 제목·슬러그·블록 없이 본문 한 덩이만 있는 마이크로 포스트. 글(post)과 달리 발행 상태 기계가 없다: 쓰는 즉시 공개, 지우면 끝(hard
 * delete).
 */
@Entity
@Table(name = "note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteEntity extends BaseCreatedEntity {

  public static final int MAX_BODY_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = MAX_BODY_LENGTH)
  private String body;

  public NoteEntity(Long userId, String body) {
    this.userId = userId;
    this.body = body;
  }

  public boolean isOwnedBy(Long viewerId) {
    return userId.equals(viewerId);
  }
}
