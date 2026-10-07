package com.example.short_link.note.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityResult;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
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
  public static final int MAX_STORED_BODY_LENGTH = 5000;
  public static final int MAX_WARNING_LENGTH = 100;
  public static final int MAX_PINS = 5;
  public static final int MIN_POLL_OPTIONS = 2;
  public static final int MAX_POLL_OPTIONS = 4;
  public static final int MAX_POLL_OPTION_LENGTH = 50;
  public static final long MIN_POLL_SECONDS = 300;
  public static final long MAX_POLL_SECONDS = 2_629_746;
  private static final int EXCERPT_LENGTH = 80;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "remote_actor_id")
  private Long remoteActorId;

  @Column(length = 512)
  private String uri;

  @Column(name = "remote_url", length = 512)
  private String remoteUrl;

  @Column(nullable = false, length = MAX_STORED_BODY_LENGTH)
  private String body;

  @Column(name = "in_reply_to_id")
  private Long inReplyToId;

  @Column(name = "conversation_id")
  private Long conversationId;

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

  @Column(name = "pinned_at")
  private Instant pinnedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NoteVisibility visibility = NoteVisibility.PUBLIC;

  @Getter(AccessLevel.NONE)
  @Column(name = "poll_options")
  private String pollOptions;

  @Column(name = "poll_expires_at")
  private Instant pollExpiresAt;

  @Column(name = "poll_multiple", nullable = false)
  private boolean pollMultiple;

  @Column(name = "poll_closed_at")
  private Instant pollClosedAt;

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
    return userId != null && userId.equals(viewerId);
  }

  public boolean isRemote() {
    return remoteActorId != null;
  }

  // A top-level note is its own conversation; a reply carries its thread's root.
  public Long conversation() {
    return conversationId != null ? conversationId : id;
  }

  public void answer(NoteEntity parent) {
    this.conversationId = parent.conversation();
  }

  public String excerpt() {
    return excerptOf(body);
  }

  public static String excerptOf(String body) {
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

  public void showTo(NoteVisibility visibility) {
    this.visibility = visibility;
  }

  public void pin(Instant at) {
    if (pinnedAt == null) {
      pinnedAt = at;
    }
  }

  public void unpin() {
    pinnedAt = null;
  }

  public boolean isPinned() {
    return pinnedAt != null;
  }

  // Option titles never contain a newline; it separates them in the one column.
  public void attachPoll(List<String> options, Instant expiresAt, boolean multiple) {
    this.pollOptions = String.join("\n", options);
    this.pollExpiresAt = expiresAt;
    this.pollMultiple = multiple;
  }

  public boolean hasPoll() {
    return pollOptions != null;
  }

  public List<String> pollOptions() {
    return pollOptions == null ? List.of() : List.of(pollOptions.split("\n"));
  }

  public boolean pollEndedBy(Instant now) {
    return pollExpiresAt != null && !pollExpiresAt.isAfter(now);
  }

  public void edit(String body, Instant at) {
    this.body = body;
    this.editedAt = at;
  }
}
