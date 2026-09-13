package com.example.short_link.notification.presentation;

import com.example.short_link.notification.application.read.LinkNotificationQueryService;
import com.example.short_link.notification.application.write.MarkLinkNotificationReadUseCase;
import com.example.short_link.notification.presentation.response.LinkNotificationsPage;
import com.example.short_link.notification.presentation.response.UnreadCountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/notifications")
public class LinkNotificationController {

  private final LinkNotificationQueryService queryService;
  private final MarkLinkNotificationReadUseCase markReadUseCase;

  public LinkNotificationController(
      LinkNotificationQueryService queryService, MarkLinkNotificationReadUseCase markReadUseCase) {
    this.queryService = queryService;
    this.markReadUseCase = markReadUseCase;
  }

  @GetMapping
  public LinkNotificationsPage list(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Long before,
      @RequestParam(defaultValue = "20") int limit) {
    return LinkNotificationsPage.from(queryService.list(userId, before, limit));
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
