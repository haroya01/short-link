package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomPerson;
import java.util.List;

public record RoomView(
    Long id,
    String name,
    String role,
    boolean approvalRequired,
    boolean hiddenFromFeed,
    long memberCount,
    List<Member> members) {

  public record Member(
      Long id, String username, String displayName, String avatarUrl, String role) {

    static Member of(RoomPerson person, boolean owner) {
      return new Member(
          person.id(),
          person.username(),
          person.displayName(),
          person.avatarUrl(),
          RoomRole.of(owner));
    }
  }
}
