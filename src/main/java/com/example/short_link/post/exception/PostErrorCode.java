package com.example.short_link.post.exception;

import org.springframework.http.HttpStatus;

public enum PostErrorCode {
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post not found: %s"),
  SLUG_CONFLICT(HttpStatus.CONFLICT, "slug already used: %s"),
  SLUG_FROZEN(HttpStatus.CONFLICT, "slug is frozen after publish: %s"),
  TITLE_REQUIRED(HttpStatus.CONFLICT, "a title is required to publish"),
  PERMISSION_DENIED(HttpStatus.FORBIDDEN, "post permission denied"),
  SCHEDULE_IN_PAST(HttpStatus.BAD_REQUEST, "scheduled time must be in the future"),
  SCHEDULE_AFTER_PUBLISH(HttpStatus.CONFLICT, "cannot schedule a post that was published"),
  UNPUBLISH_NOT_PUBLISHED(HttpStatus.CONFLICT, "only published posts can be unpublished"),
  REPUBLISH_NOT_UNPUBLISHED(HttpStatus.CONFLICT, "only unpublished posts can be republished"),
  BACK_TO_DRAFT_NOT_SCHEDULED(HttpStatus.CONFLICT, "only scheduled posts can return to draft"),
  REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "revision not found: %s"),
  POST_GONE(HttpStatus.GONE, "post is no longer available: %s"),
  SERIES_NOT_FOUND(HttpStatus.NOT_FOUND, "series not found: %s"),
  SERIES_SLUG_CONFLICT(HttpStatus.CONFLICT, "series slug already used: %s"),
  SERIES_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "series permission denied"),
  COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "comment not found: %s"),
  COMMENT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "comment permission denied"),
  COMMENT_PARENT_INVALID(HttpStatus.BAD_REQUEST, "parent comment invalid"),
  WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "blog webhook not found: %s"),
  INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST, "webhook url must be a public https endpoint"),
  TOO_MANY_WEBHOOKS(HttpStatus.CONFLICT, "too many blog webhooks (max %s)"),
  BOOKMARK_FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "bookmark folder not found: %s"),
  BOOKMARK_FOLDER_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "a folder name is required"),
  BOOKMARK_FOLDER_NAME_CONFLICT(HttpStatus.CONFLICT, "bookmark folder name already used: %s"),
  BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "bookmark not found for post: %s"),
  NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "note not found: %s"),
  NOTE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "note permission denied"),
  NOTE_BODY_REQUIRED(HttpStatus.BAD_REQUEST, "note body is required"),
  NOTE_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "note body exceeds 500 characters"),
  HIGHLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "highlight not found: %s"),
  HIGHLIGHT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "highlight permission denied");

  private final HttpStatus status;
  private final String template;

  PostErrorCode(HttpStatus status, String template) {
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
