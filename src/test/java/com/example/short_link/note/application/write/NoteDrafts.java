package com.example.short_link.note.application.write;

import java.util.List;

public final class NoteDrafts {

  private final String body;
  private List<NoteDraft.Image> images = List.of();
  private Long quotedPostId;
  private Long inReplyToId;
  private Long quotedNoteId;
  private String contentWarning;
  private boolean sensitive;
  private String visibility;
  private NoteDraft.Poll poll;
  private String language;
  private String replyPolicy;
  private Long roomId;

  private NoteDrafts(String body) {
    this.body = body;
  }

  public static NoteDrafts draft(String body) {
    return new NoteDrafts(body);
  }

  public NoteDrafts images(List<NoteDraft.Image> images) {
    this.images = images;
    return this;
  }

  public NoteDrafts quotingPost(Long postId) {
    this.quotedPostId = postId;
    return this;
  }

  public NoteDrafts replyingTo(Long noteId) {
    this.inReplyToId = noteId;
    return this;
  }

  public NoteDrafts quotingNote(Long noteId) {
    this.quotedNoteId = noteId;
    return this;
  }

  public NoteDrafts warning(String contentWarning) {
    this.contentWarning = contentWarning;
    return this;
  }

  public NoteDrafts sensitive(boolean sensitive) {
    this.sensitive = sensitive;
    return this;
  }

  public NoteDrafts visibility(String visibility) {
    this.visibility = visibility;
    return this;
  }

  public NoteDrafts poll(NoteDraft.Poll poll) {
    this.poll = poll;
    return this;
  }

  public NoteDrafts language(String language) {
    this.language = language;
    return this;
  }

  public NoteDrafts replyPolicy(String replyPolicy) {
    this.replyPolicy = replyPolicy;
    return this;
  }

  public NoteDrafts inRoom(Long roomId) {
    this.roomId = roomId;
    return this;
  }

  public NoteDraft build() {
    return new NoteDraft(
        body,
        images,
        quotedPostId,
        inReplyToId,
        quotedNoteId,
        contentWarning,
        sensitive,
        visibility,
        poll,
        language,
        replyPolicy,
        roomId);
  }
}
