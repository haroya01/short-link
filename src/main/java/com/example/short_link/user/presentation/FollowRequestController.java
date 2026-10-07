package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.FollowRequestView;
import com.example.short_link.user.application.write.FollowRequestUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/follow-requests")
@RequiredArgsConstructor
public class FollowRequestController {

  private final FollowRequestUseCase followRequests;

  @GetMapping
  public List<FollowRequestView> pending(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "0") int page) {
    return followRequests.pending(userId, page);
  }

  @PostMapping("/{username}/authorize")
  public ResponseEntity<Void> authorize(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    followRequests.authorize(userId, username);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{username}/reject")
  public ResponseEntity<Void> reject(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    followRequests.reject(userId, username);
    return ResponseEntity.noContent().build();
  }
}
