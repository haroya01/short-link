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
@Table(name = "note_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteMediaEntity extends BaseCreatedEntity {

  public static final int MAX_PER_NOTE = 4;
  public static final int MAX_ALT_TEXT_LENGTH = 1500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false)
  private Long noteId;

  @Column(nullable = false)
  private int position;

  @Column(name = "storage_key", nullable = false, length = 256)
  private String storageKey;

  @Column(nullable = false, length = 512)
  private String url;

  @Column(name = "content_type", nullable = false, length = 32)
  private String contentType;

  @Column(name = "alt_text", length = MAX_ALT_TEXT_LENGTH)
  private String altText;

  public NoteMediaEntity(
      Long noteId,
      int position,
      String storageKey,
      String url,
      String contentType,
      String altText) {
    this.noteId = noteId;
    this.position = position;
    this.storageKey = storageKey;
    this.url = url;
    this.contentType = contentType;
    this.altText = altText;
  }
}
