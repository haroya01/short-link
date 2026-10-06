package com.example.short_link.note.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityResult;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "note")
@SqlResultSetMapping(
    name = NoteEntity.FEED_MAPPING,
    entities = @EntityResult(entityClass = NoteEntity.class),
    columns = @ColumnResult(name = "reposter_id", type = Long.class))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteEntity extends BaseCreatedEntity {

  public static final String FEED_MAPPING = "NoteEntity.feed";

  // MySQL VARCHAR(500) counts code points, so length checks use codePointCount, not length().
  public static final int MAX_BODY_LENGTH = 500;
  public static final int MAX_WARNING_LENGTH = 100;
  private static final int EXCERPT_LENGTH = 80;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = MAX_BODY_LENGTH)
  private String body;

  @Column(name = "in_reply_to_id")
  private Long inReplyToId;

  @Column(name = "quoted_post_id")
  private Long quotedPostId;

  @Column(name = "quoted_note_id")
  private Long quotedNoteId;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(name = "content_warning", length = MAX_WARNING_LENGTH)
  private String contentWarning;

  @Column(name = "marked_sensitive", nullable = false)
  private boolean sensitive;

  public NoteEntity(Long userId, String body, Long inReplyToId, Long quotedPostId) {
    this(userId, body, inReplyToId, quotedPostId, null);
  }

  public NoteEntity(
      Long userId, String body, Long inReplyToId, Long quotedPostId, Long quotedNoteId) {
    this.userId = userId;
    this.body = body;
    this.inReplyToId = inReplyToId;
    this.quotedPostId = quotedPostId;
    this.quotedNoteId = quotedNoteId;
  }

  public boolean isOwnedBy(Long viewerId) {
    return userId.equals(viewerId);
  }

  public String excerpt() {
    String flat = body == null ? "" : body.strip().replaceAll("\\s+", " ");
    if (flat.codePointCount(0, flat.length()) <= EXCERPT_LENGTH) {
      return flat;
    }
    return flat.substring(0, flat.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
  }

  // As on Mastodon, a warning hides the photos too, so a note with one is always sensitive.
  public void markContent(String warning, boolean sensitive) {
    this.contentWarning = warning;
    this.sensitive = sensitive || warning != null;
  }

  public void edit(String body, Instant at) {
    this.body = body;
    this.editedAt = at;
  }
}
