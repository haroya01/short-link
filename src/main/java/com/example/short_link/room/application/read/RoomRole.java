package com.example.short_link.room.application.read;

public final class RoomRole {

  public static final String OWNER = "owner";
  public static final String MEMBER = "member";

  private RoomRole() {}

  public static String of(boolean owner) {
    return owner ? OWNER : MEMBER;
  }
}
