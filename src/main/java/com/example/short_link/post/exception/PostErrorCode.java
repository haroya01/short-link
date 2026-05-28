package com.example.short_link.post.exception;

import org.springframework.http.HttpStatus;

public enum PostErrorCode {
  POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post not found: %s"),
  SLUG_CONFLICT(HttpStatus.CONFLICT, "slug already used: %s"),
  SLUG_FROZEN(HttpStatus.CONFLICT, "slug is frozen after publish: %s"),
  PERMISSION_DENIED(HttpStatus.FORBIDDEN, "post permission denied"),
  SCHEDULE_IN_PAST(HttpStatus.BAD_REQUEST, "scheduled time must be in the future"),
  SCHEDULE_AFTER_PUBLISH(HttpStatus.CONFLICT, "cannot schedule a post that was published"),
  UNPUBLISH_NOT_PUBLISHED(HttpStatus.CONFLICT, "only published posts can be unpublished"),
  REPUBLISH_NOT_UNPUBLISHED(HttpStatus.CONFLICT, "only unpublished posts can be republished"),
  BACK_TO_DRAFT_NOT_SCHEDULED(HttpStatus.CONFLICT, "only scheduled posts can return to draft"),
  REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "revision not found: %s"),
  POST_GONE(HttpStatus.GONE, "post is no longer available: %s");

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
