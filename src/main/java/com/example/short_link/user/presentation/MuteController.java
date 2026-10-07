package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.MuteQueryService;
import com.example.short_link.user.application.read.MuteStatus;
import com.example.short_link.user.application.read.MutedUserView;
import com.example.short_link.user.application.write.MuteUseCase;
import com.example.short_link.user.presentation.request.MuteRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MuteController {

  private final MuteUseCase muteUseCase;
  private final MuteQueryService muteQueryService;

  @PutMapping("/api/v1/users/{username}/mute")
  public MuteStatus mute(
      @AuthenticationPrincipal Long userId,
      @PathVariable String username,
      @RequestBody(required = false) MuteRequest request) {
    return muteUseCase.mute(
        userId,
        username,
        request == null ? null : request.notifications(),
        request == null ? null : request.duration());
  }

  @DeleteMapping("/api/v1/users/{username}/mute")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unmute(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    muteUseCase.unmute(userId, username);
  }

  @GetMapping("/api/v1/users/{username}/mute")
  public MuteStatus status(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    return muteUseCase.status(userId, username);
  }

  @GetMapping("/api/v1/users/me/mutes")
  public List<MutedUserView> myMutes(@AuthenticationPrincipal Long userId) {
    return muteQueryService.myMutes(userId);
  }
}
