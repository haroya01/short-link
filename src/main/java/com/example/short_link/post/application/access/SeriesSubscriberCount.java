package com.example.short_link.post.application.access;

import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeriesSubscriberCount {

  private final SeriesRepository seriesRepository;
  private final SeriesSubscriptionRepository subscriptions;

  public long shownTo(Long viewerId, Long seriesId) {
    return seriesRepository
        .findById(seriesId)
        .map(series -> shownTo(viewerId, seriesId, series.getUserId()))
        .orElse(0L);
  }

  public long shownTo(Long viewerId, Long seriesId, Long ownerId) {
    boolean shown =
        ownerId.equals(viewerId)
            || !seriesRepository.findPublishedSummaries(List.of(seriesId)).isEmpty();
    return shown ? subscriptions.countBySeriesId(seriesId) : 0;
  }
}
