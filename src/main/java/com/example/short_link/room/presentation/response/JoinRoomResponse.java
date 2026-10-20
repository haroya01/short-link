package com.example.short_link.room.presentation.response;

import com.example.short_link.room.application.write.RoomInviteService;
import java.util.Locale;

public record JoinRoomResponse(String status, Long roomId) {

  public static JoinRoomResponse of(RoomInviteService.JoinResult result) {
    return new JoinRoomResponse(result.status().name().toLowerCase(Locale.ROOT), result.roomId());
  }
}
