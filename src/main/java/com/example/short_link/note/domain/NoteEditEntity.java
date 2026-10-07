package com.example.short_link.note.domain;

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
@Table(name = "note_edit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteEditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false)
  private Long noteId;

  @Column(nullable = false, length = NoteEntity.MAX_BODY_LENGTH)
  private String body;

  @Column(name = "content_warning", length = NoteEntity.MAX_WARNING_LENGTH)
  private String contentWarning;

  @Column(name = "marked_sensitive", nullable = false)
  private boolean sensitive;

  @Column(name = "written_at", nullable = false)
  private Instant writtenAt;

  public NoteEditEntity(Long noteId, NoteVersion version) {
    this.noteId = noteId;
    this.body = version.body();
    this.contentWarning = version.contentWarning();
    this.sensitive = version.sensitive();
    this.writtenAt = version.at();
  }

  public NoteVersion version() {
    return new NoteVersion(body, contentWarning, sensitive, writtenAt);
  }
}
