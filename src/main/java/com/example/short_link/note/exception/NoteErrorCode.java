package com.example.short_link.note.exception;

import org.springframework.http.HttpStatus;

public enum NoteErrorCode {
  NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "note not found: %s"),
  NOTE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "note permission denied"),
  NOTE_BODY_REQUIRED(HttpStatus.BAD_REQUEST, "note body or an image is required"),
  NOTE_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "note body exceeds %s characters"),
  NOTE_TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "a note holds at most %s images"),
  NOTE_ALT_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "alt text exceeds %s characters"),
  NOTE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "%s"),
  NOTE_IMAGES_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "image storage is not configured"),
  NOTE_QUOTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "quoted post is not published: %s"),
  NOTE_QUOTED_NOTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "quoted note not found: %s"),
  NOTE_QUOTE_CONFLICT(HttpStatus.BAD_REQUEST, "a note quotes a post or a note, not both"),
  NOTE_REPLY_BLOCKED(HttpStatus.FORBIDDEN, "cannot reply to this note"),
  NOTE_INTERACTION_BLOCKED(HttpStatus.FORBIDDEN, "cannot repost or quote this note");

  private final HttpStatus status;
  private final String template;

  NoteErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
