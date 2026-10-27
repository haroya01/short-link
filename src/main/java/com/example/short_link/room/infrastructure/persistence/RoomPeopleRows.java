package com.example.short_link.room.infrastructure.persistence;

import com.example.short_link.room.domain.RoomPerson;
import java.util.ArrayList;
import java.util.List;

final class RoomPeopleRows {

  static final String PERSON = "u.id, u.username, u.display_name, u.avatar_url";

  private RoomPeopleRows() {}

  static List<RoomPerson> people(List<?> rows) {
    List<RoomPerson> people = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      people.add(person((Object[]) raw));
    }
    return people;
  }

  static RoomPerson person(Object[] c) {
    return new RoomPerson(((Number) c[0]).longValue(), (String) c[1], (String) c[2], (String) c[3]);
  }

  static boolean truth(Object value) {
    return value instanceof Boolean b ? b : value != null && ((Number) value).intValue() != 0;
  }
}
