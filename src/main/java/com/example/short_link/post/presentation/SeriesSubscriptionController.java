package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.SeriesSubscriptionQueryService;
import com.example.short_link.post.application.read.SeriesSubscriptionStatus;
import com.example.short_link.post.application.write.SubscribeSeriesUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscribe / unsubscribe to a series. All endpoints require auth (subscriptions are per-user) —
 * covered by SecurityConfig's {@code anyRequest().authenticated()}. The "my subscriptions" list
 * lives under {@code /users/me/...} (mirroring /users/me/likes) to avoid colliding with {@code GET
 * /api/v1/series/{id}}.
 */
@RestController
@RequiredArgsConstructor
public class SeriesSubscriptionController {

  private final SubscribeSeriesUseCase subscribeSeriesUseCase;
  private final SeriesSubscriptionQueryService seriesSubscriptionQueryService;

  @GetMapping("/api/v1/users/me/series-subscriptions")
  public List<Long> mySubscriptions(@AuthenticationPrincipal Long userId) {
    return seriesSubscriptionQueryService.mySubscriptions(userId);
  }

  @GetMapping("/api/v1/series/{seriesId}/subscription")
  public SeriesSubscriptionStatus status(
      @AuthenticationPrincipal Long userId, @PathVariable Long seriesId) {
    return seriesSubscriptionQueryService.status(userId, seriesId);
  }

  @PutMapping("/api/v1/series/{seriesId}/subscription")
  public SeriesSubscriptionStatus subscribe(
      @AuthenticationPrincipal Long userId, @PathVariable Long seriesId) {
    return subscribeSeriesUseCase.subscribe(userId, seriesId);
  }

  @DeleteMapping("/api/v1/series/{seriesId}/subscription")
  public SeriesSubscriptionStatus unsubscribe(
      @AuthenticationPrincipal Long userId, @PathVariable Long seriesId) {
    return subscribeSeriesUseCase.unsubscribe(userId, seriesId);
  }
}
