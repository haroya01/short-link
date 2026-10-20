package com.example.short_link.room.presentation;

import com.example.short_link.room.application.read.RoomInviteLanding;
import com.example.short_link.room.application.read.RoomQueryService;
import com.example.short_link.room.application.write.RoomInviteService;
import com.example.short_link.room.presentation.response.JoinRoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoomInviteController {

  private final RoomQueryService query;
  private final RoomInviteService invites;

  @GetMapping("/api/v1/public/room-invites/{code}")
  public RoomInviteLanding landing(@PathVariable String code) {
    return query.landing(code);
  }

  @PostMapping("/api/v1/room-invites/{code}/join")
  public JoinRoomResponse join(@AuthenticationPrincipal Long userId, @PathVariable String code) {
    return JoinRoomResponse.of(invites.join(userId, code));
  }
}
