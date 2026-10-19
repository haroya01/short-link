package com.example.short_link.room.exception;

import org.springframework.http.HttpStatus;

public enum RoomErrorCode {
  ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "room not found: %s"),
  ROOM_NAME_INVALID(HttpStatus.BAD_REQUEST, "a room name is 1 to %s characters"),
  ROOM_OWNER_ONLY(HttpStatus.FORBIDDEN, "only the room's owner can do this"),
  ROOM_FULL(HttpStatus.CONFLICT, "a room holds at most %s members"),
  ROOM_REMOVED(HttpStatus.FORBIDDEN, "the owner removed you from this room"),
  ROOM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "not a member of this room: %s"),
  ROOM_INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "invite not found"),
  ROOM_INVITE_UNUSABLE(HttpStatus.GONE, "this invite lets no one in: %s"),
  ROOM_INVITE_INVALID(
      HttpStatus.BAD_REQUEST,
      "an invite is used 1 to %s times and lasts 5 minutes to 30 days, or without either limit"),
  ROOM_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "no request to join from: %s"),
  ROOM_SUGGESTION_INVALID(HttpStatus.BAD_REQUEST, "dismiss 1 to %s suggested friends at once");

  private final HttpStatus status;
  private final String template;

  RoomErrorCode(HttpStatus status, String template) {
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
