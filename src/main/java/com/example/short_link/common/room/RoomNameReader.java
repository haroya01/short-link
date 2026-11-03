package com.example.short_link.common.room;

import java.util.Optional;

public interface RoomNameReader {

  Optional<String> nameOf(Long roomId);
}
