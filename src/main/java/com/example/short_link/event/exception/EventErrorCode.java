package com.example.short_link.event.exception;

import org.springframework.http.HttpStatus;

public enum EventErrorCode {
  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "event not found: %s"),
  EVENT_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "event permission denied"),
  EVENT_CANCELED(HttpStatus.CONFLICT, "event canceled: %s"),
  EVENT_FULL(HttpStatus.CONFLICT, "event full: %s"),
  EVENT_REGISTRATION_CLOSED(HttpStatus.CONFLICT, "registration closed: %s"),
  ALREADY_REGISTERED(HttpStatus.CONFLICT, "already registered"),
  INVALID_CONTACT(HttpStatus.BAD_REQUEST, "invalid contact for field type %s"),
  INVALID_QUESTIONS(HttpStatus.BAD_REQUEST, "invalid questions: %s"),
  INVALID_ANSWER(HttpStatus.BAD_REQUEST, "invalid answer for question: %s"),
  REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "registration not found"),
  SLUG_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, "could not allocate event slug");

  private final HttpStatus status;
  private final String template;

  EventErrorCode(HttpStatus status, String template) {
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
