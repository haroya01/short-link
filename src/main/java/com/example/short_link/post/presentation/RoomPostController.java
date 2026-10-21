package com.example.short_link.post.presentation;

import com.example.short_link.post.application.write.RoomPostEraser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoomPostController {

  private final RoomPostEraser eraser;

  @DeleteMapping("/api/v1/rooms/my-posts")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void deleteMyRoomPosts(@AuthenticationPrincipal Long userId, @RequestParam Long roomId) {
    eraser.erasePostsBy(userId, roomId);
  }
}
