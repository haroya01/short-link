package com.example.short_link.notification.presentation;

import com.example.short_link.notification.application.policy.NotificationPolicyService;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.presentation.request.NotificationSenderRequest;
import com.example.short_link.notification.presentation.request.UpdateNotificationPolicyRequest;
import com.example.short_link.notification.presentation.response.NotificationRequestResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationPolicyController {

  private final NotificationPolicyService policies;

  @GetMapping("/policy")
  public NotificationPolicy policy(@AuthenticationPrincipal Long userId) {
    return policies.policy(userId);
  }

  @PutMapping("/policy")
  public NotificationPolicy update(
      @AuthenticationPrincipal Long userId, @RequestBody UpdateNotificationPolicyRequest body) {
    return policies.update(
        userId,
        body.forNotFollowing(),
        body.forNotFollowers(),
        body.forNewAccounts(),
        body.forPrivateMentions());
  }

  @GetMapping("/requests")
  public List<NotificationRequestResponse> requests(@AuthenticationPrincipal Long userId) {
    return policies.requests(userId).stream().map(NotificationRequestResponse::from).toList();
  }

  @PostMapping("/requests/accept")
  public ResponseEntity<Void> accept(
      @AuthenticationPrincipal Long userId, @RequestBody NotificationSenderRequest body) {
    policies.accept(userId, body.actorUserId(), body.actorRemoteId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/requests/dismiss")
  public ResponseEntity<Void> dismiss(
      @AuthenticationPrincipal Long userId, @RequestBody NotificationSenderRequest body) {
    policies.dismiss(userId, body.actorUserId(), body.actorRemoteId());
    return ResponseEntity.noContent().build();
  }
}
