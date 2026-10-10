package com.example.short_link.note.exception;

import org.springframework.http.HttpStatus;

public enum NoteErrorCode {
  NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "note not found: %s"),
  NOTE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "note permission denied"),
  NOTE_BODY_REQUIRED(HttpStatus.BAD_REQUEST, "note body or an image is required"),
  NOTE_BODY_TOO_LONG(HttpStatus.BAD_REQUEST, "note body exceeds %s characters"),
  NOTE_WARNING_TOO_LONG(HttpStatus.BAD_REQUEST, "content warning exceeds %s characters"),
  NOTE_PIN_LIMIT(HttpStatus.BAD_REQUEST, "at most %s notes can be pinned"),
  NOTE_PIN_REPLY(HttpStatus.BAD_REQUEST, "a reply cannot be pinned"),
  NOTE_VISIBILITY_INVALID(HttpStatus.BAD_REQUEST, "unknown visibility: %s"),
  NOTE_LANGUAGE_INVALID(HttpStatus.BAD_REQUEST, "a language is an ISO 639 code like ko: %s"),
  NOTE_PIN_DIRECT(HttpStatus.BAD_REQUEST, "a direct note cannot be pinned"),
  NOTE_LIST_NOT_FOUND(HttpStatus.NOT_FOUND, "list not found: %s"),
  NOTE_LIST_LIMIT(HttpStatus.BAD_REQUEST, "at most %s lists"),
  NOTE_LIST_MEMBER_LIMIT(HttpStatus.BAD_REQUEST, "a list holds at most %s people"),
  NOTE_LIST_TITLE_INVALID(HttpStatus.BAD_REQUEST, "a list title is 1 to %s characters"),
  NOTE_LIST_SELF(HttpStatus.BAD_REQUEST, "you are not added to your own list"),
  NOTE_FILTER_NOT_FOUND(HttpStatus.NOT_FOUND, "filter not found: %s"),
  NOTE_FILTER_LIMIT(HttpStatus.BAD_REQUEST, "at most %s filters"),
  NOTE_FILTER_INVALID(
      HttpStatus.BAD_REQUEST,
      "a filter has a phrase of 1 to 100 characters, at least one context, warn or hide, and lasts"
          + " a minute to a year or forever"),
  NOTE_POLL_INVALID(
      HttpStatus.BAD_REQUEST,
      "a poll has 2 to 4 different options of up to 50 characters and lasts 5 minutes to a month"),
  NOTE_POLL_WITH_MEDIA(HttpStatus.BAD_REQUEST, "a note has a poll or images, not both"),
  NOTE_POLL_NOT_FOUND(HttpStatus.NOT_FOUND, "note has no poll: %s"),
  NOTE_POLL_ENDED(HttpStatus.BAD_REQUEST, "the poll has ended"),
  NOTE_POLL_OWN(HttpStatus.BAD_REQUEST, "you do not vote in your own poll"),
  NOTE_POLL_ALREADY_VOTED(HttpStatus.CONFLICT, "you already voted in this poll"),
  NOTE_POLL_BLOCKED(HttpStatus.FORBIDDEN, "cannot vote in this poll"),
  NOTE_POLL_CHOICES_INVALID(
      HttpStatus.BAD_REQUEST, "choose one option, or several in a multiple-choice poll"),
  NOTE_NOT_SHAREABLE(
      HttpStatus.BAD_REQUEST, "only public and unlisted notes can be reposted or quoted"),
  NOTE_TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "a note holds at most %s images"),
  NOTE_THREAD_SIZE(HttpStatus.BAD_REQUEST, "a thread holds 2 to %s notes"),
  NOTE_ALT_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "alt text exceeds %s characters"),
  NOTE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "%s"),
  NOTE_IMAGES_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "image storage is not configured"),
  NOTE_QUOTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "quoted post is not published: %s"),
  NOTE_QUOTED_NOTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "quoted note not found: %s"),
  NOTE_QUOTE_CONFLICT(HttpStatus.BAD_REQUEST, "a note quotes a post or a note, not both"),
  NOTE_REPLY_BLOCKED(HttpStatus.FORBIDDEN, "cannot reply to this note"),
  NOTE_REPLY_RESTRICTED(HttpStatus.FORBIDDEN, "the writer of this thread limited who can reply"),
  NOTE_REPLY_POLICY_INVALID(
      HttpStatus.BAD_REQUEST, "who can reply is everyone, following or mentioned: %s"),
  NOTE_REPLY_POLICY_ON_REPLY(
      HttpStatus.BAD_REQUEST, "who can reply is set on the first note of a thread"),
  NOTE_NOT_A_REPLY(HttpStatus.BAD_REQUEST, "only a reply can be hidden"),
  NOTE_INTERACTION_BLOCKED(HttpStatus.FORBIDDEN, "cannot repost or quote this note"),
  NOTE_REMOTE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "notes from other servers cannot be quoted yet"),
  NOTE_SCHEDULE_TOO_SOON(
      HttpStatus.UNPROCESSABLE_ENTITY, "schedule a note at least 5 minutes ahead"),
  NOTE_SCHEDULE_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY, "too many scheduled notes: %s"),
  NOTE_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "scheduled note not found: %s");

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
