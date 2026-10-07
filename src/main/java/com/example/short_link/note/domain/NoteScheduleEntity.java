package com.example.short_link.note.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
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

@Entity
@Table(name = "note_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteScheduleEntity extends BaseCreatedEntity {

  public static final int MAX_PENDING = 300;
  public static final int MAX_PER_DAY = 25;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "publish_at", nullable = false)
  private Instant publishAt;

  @Column(nullable = false, columnDefinition = "json")
  private String draft;

  @Column(length = 64)
  private String failure;

  public NoteScheduleEntity(Long userId, Instant publishAt, String draft) {
    this.userId = userId;
    this.publishAt = publishAt;
    this.draft = draft;
  }

  public void moveTo(Instant at) {
    publishAt = at;
    failure = null;
  }

  public void fail(String code) {
    failure = code;
  }
}
