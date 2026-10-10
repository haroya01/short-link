package com.example.short_link.note.application.write;

import java.util.List;

public record NoteDraft(
    String body,
    List<Image> images,
    Long quotedPostId,
    Long inReplyToId,
    Long quotedNoteId,
    String contentWarning,
    boolean sensitive,
    String visibility,
    Poll poll,
    String language,
    String replyPolicy) {

  public NoteDraft(
      String body,
      List<Image> images,
      Long quotedPostId,
      Long inReplyToId,
      Long quotedNoteId,
      String contentWarning,
      boolean sensitive,
      String visibility,
      Poll poll,
      String language) {
    this(
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
        null);
  }

  public NoteDraft(
      String body,
      List<Image> images,
      Long quotedPostId,
      Long inReplyToId,
      Long quotedNoteId,
      String contentWarning,
      boolean sensitive,
      String visibility,
      Poll poll) {
    this(
        body,
        images,
        quotedPostId,
        inReplyToId,
        quotedNoteId,
        contentWarning,
        sensitive,
        visibility,
        poll,
        null);
  }

  public NoteDraft(
      String body,
      List<Image> images,
      Long quotedPostId,
      Long inReplyToId,
      Long quotedNoteId,
      String contentWarning,
      boolean sensitive,
      String visibility) {
    this(
        body,
        images,
        quotedPostId,
        inReplyToId,
        quotedNoteId,
        contentWarning,
        sensitive,
        visibility,
        null);
  }

  // The next note of a thread answers the one before it and keeps that note's visibility, warning
  // and sensitive mark, unless it carries a warning of its own; who may reply is the thread's.
  NoteDraft continuing(Long previousId, String previousWarning, boolean previousSensitive) {
    return new NoteDraft(
        body,
        images,
        quotedPostId,
        previousId,
        quotedNoteId,
        contentWarning == null || contentWarning.isBlank() ? previousWarning : contentWarning,
        sensitive || previousSensitive,
        null,
        poll,
        language,
        null);
  }

  public NoteDraft(String body, List<Image> images, Long quotedPostId, Long inReplyToId) {
    this(body, images, quotedPostId, inReplyToId, null);
  }

  public NoteDraft(
      String body, List<Image> images, Long quotedPostId, Long inReplyToId, Long quotedNoteId) {
    this(body, images, quotedPostId, inReplyToId, quotedNoteId, null, false, null);
  }

  public NoteDraft(
      String body,
      List<Image> images,
      Long quotedPostId,
      Long inReplyToId,
      Long quotedNoteId,
      String contentWarning,
      boolean sensitive) {
    this(body, images, quotedPostId, inReplyToId, quotedNoteId, contentWarning, sensitive, null);
  }

  // Width and height are the picture as the poster's device shows it (EXIF orientation applied);
  // the server never opens the upload, so they are trusted only for layout.
  public record Image(String key, String altText, Integer width, Integer height) {
    public Image(String key, String altText) {
      this(key, altText, null, null);
    }
  }

  public record Poll(List<String> options, Long expiresIn, boolean multiple) {}
}
