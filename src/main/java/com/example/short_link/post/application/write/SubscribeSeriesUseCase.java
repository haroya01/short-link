package com.example.short_link.post.application.write;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.post.application.read.SeriesSubscriptionStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscribe / unsubscribe to a series ("구독"). Idempotent — subscribing an already-subscribed series
 * is a no-op, unsubscribing one that isn't subscribed is a no-op. New episodes of subscribed series
 * surface in the subscriber's following feed (see {@code PublicFeedQueryService#feedFollowing}).
 */
@Service
@RequiredArgsConstructor
public class SubscribeSeriesUseCase {

  private final SeriesRepository seriesRepository;
  private final SeriesSubscriptionRepository subscriptionRepository;
  private final ApplicationEventPublisher events;

  @Transactional
  public SeriesSubscriptionStatus subscribe(Long userId, Long seriesId) {
    SeriesEntity series = requireSeries(seriesId);
    if (!subscriptionRepository.existsByUserIdAndSeriesId(userId, seriesId)) {
      subscriptionRepository.save(new SeriesSubscriptionEntity(userId, seriesId));
      // Notify the series owner of a new subscriber — never for subscribing your own series.
      if (!series.getUserId().equals(userId)) {
        events.publishEvent(
            BlogInteractionEvent.seriesSubscribe(
                series.getUserId(), userId, seriesId, series.getTitle(), Instant.now()));
      }
    }
    return new SeriesSubscriptionStatus(true, subscriptionRepository.countBySeriesId(seriesId));
  }

  @Transactional
  public SeriesSubscriptionStatus unsubscribe(Long userId, Long seriesId) {
    subscriptionRepository
        .findByUserIdAndSeriesId(userId, seriesId)
        .ifPresent(subscriptionRepository::delete);
    return new SeriesSubscriptionStatus(false, subscriptionRepository.countBySeriesId(seriesId));
  }

  private SeriesEntity requireSeries(Long seriesId) {
    return seriesRepository
        .findById(seriesId)
        .orElseThrow(
            () -> new PostException(PostErrorCode.SERIES_NOT_FOUND, String.valueOf(seriesId)));
  }
}
