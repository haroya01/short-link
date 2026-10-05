package com.example.short_link.federation.application.inbox;

public record InboxOutcome(Kind kind, String reason) {

  public enum Kind {
    ACCEPTED,
    IGNORED,
    UNAUTHORIZED,
    MALFORMED,
    NOT_FOUND
  }

  static InboxOutcome accepted(String reason) {
    return new InboxOutcome(Kind.ACCEPTED, reason);
  }

  static InboxOutcome ignored(String reason) {
    return new InboxOutcome(Kind.IGNORED, reason);
  }

  static InboxOutcome unauthorized(String reason) {
    return new InboxOutcome(Kind.UNAUTHORIZED, reason);
  }

  static InboxOutcome malformed(String reason) {
    return new InboxOutcome(Kind.MALFORMED, reason);
  }

  static InboxOutcome notFound() {
    return new InboxOutcome(Kind.NOT_FOUND, "inbox");
  }
}
