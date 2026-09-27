package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesSubscriptionQueryService {

  private final SeriesSubscriptionRepository subscriptionRepository;

  public SeriesSubscriptionStatus status(Long userId, Long seriesId) {
    boolean subscribed =
        userId != null && subscriptionRepository.existsByUserIdAndSeriesId(userId, seriesId);
    return new SeriesSubscriptionStatus(
        subscribed, subscriptionRepository.countBySeriesId(seriesId));
  }

  public List<Long> mySubscriptions(Long userId) {
    return subscriptionRepository.findSubscribedSeriesIds(userId);
  }
}
