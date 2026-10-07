package com.example.short_link.note.domain;

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

@Entity
@Table(name = "note_list")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteListEntity extends BaseCreatedEntity {

  public static final int MAX_TITLE_LENGTH = 50;
  public static final int MAX_LISTS = 50;
  public static final int MAX_MEMBERS = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  public NoteListEntity(Long userId, String title) {
    this.userId = userId;
    this.title = title;
  }

  public void rename(String title) {
    this.title = title;
  }
}
