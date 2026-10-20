package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomPerson;
import java.util.List;

public record RoomSuggestions(List<RoomPerson> friends) {}
