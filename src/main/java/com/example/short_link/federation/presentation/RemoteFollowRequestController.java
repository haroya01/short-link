package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationFollowers;
import com.example.short_link.federation.application.RemoteFollowRequestView;
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
@RequestMapping("/api/v1/federation/follow-requests")
@RequiredArgsConstructor
public class RemoteFollowRequestController {

  private final FederationFollowers followers;

  @GetMapping
  public List<RemoteFollowRequestView> pending(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "0") int page) {
    return followers.pending(userId, page);
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<Void> authorize(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    followers.authorize(userId, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<Void> reject(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    followers.reject(userId, id);
    return ResponseEntity.noContent().build();
  }
}
