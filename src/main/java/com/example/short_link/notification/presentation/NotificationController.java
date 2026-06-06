package com.example.short_link.notification.presentation;

import com.example.short_link.notification.application.read.NotificationQueryService;
import com.example.short_link.notification.application.write.MarkNotificationReadUseCase;
import com.example.short_link.notification.presentation.response.NotificationsPage;
import com.example.short_link.notification.presentation.response.UnreadCountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's in-app notifications. Every endpoint is scoped to the authenticated
 * principal — there's no cross-user access — so no path carries a user id.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationQueryService queryService;
  private final MarkNotificationReadUseCase markReadUseCase;

  public NotificationController(
      NotificationQueryService queryService, MarkNotificationReadUseCase markReadUseCase) {
    this.queryService = queryService;
    this.markReadUseCase = markReadUseCase;
  }

  @GetMapping
  public NotificationsPage list(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "20") int limit) {
    return NotificationsPage.from(queryService.list(userId, before, limit));
  }

  @GetMapping("/unread-count")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal Long userId) {
    return new UnreadCountResponse(queryService.unreadCount(userId));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    markReadUseCase.markRead(userId, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public UnreadCountResponse markAllRead(@AuthenticationPrincipal Long userId) {
    markReadUseCase.markAllRead(userId);
    return new UnreadCountResponse(0);
  }
}
