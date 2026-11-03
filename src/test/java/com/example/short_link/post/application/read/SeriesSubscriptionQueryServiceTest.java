package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.access.SeriesSubscriberCount;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesSummary;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeriesSubscriptionQueryServiceTest {

  @Mock private SeriesSubscriptionRepository subscriptionRepository;
  @Mock private SeriesRepository seriesRepository;

  private SeriesSubscriptionQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new SeriesSubscriptionQueryService(
            subscriptionRepository,
            new SeriesSubscriberCount(seriesRepository, subscriptionRepository));
  }

  @Test
  void aPublishedSeriesShowsItsCountToAnyoneAndSaysWhetherTheViewerIsSubscribed() {
    when(seriesRepository.findById(5L)).thenReturn(Optional.of(series(1L)));
    when(seriesRepository.findPublishedSummaries(List.of(5L)))
        .thenReturn(List.of(new SeriesSummary(5L, "s", "S", 2)));
    when(subscriptionRepository.existsByUserIdAndSeriesId(9L, 5L)).thenReturn(true);
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(4L);

    assertThat(service.status(9L, 5L)).isEqualTo(new SeriesSubscriptionStatus(true, 4L));
    assertThat(service.status(null, 5L)).isEqualTo(new SeriesSubscriptionStatus(false, 4L));
  }

  @Test
  void aSeriesWithNothingPublishedShowsItsCountOnlyToItsOwner() {
    when(seriesRepository.findById(5L)).thenReturn(Optional.of(series(1L)));
    when(seriesRepository.findPublishedSummaries(List.of(5L))).thenReturn(List.of());
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(4L);

    assertThat(service.status(9L, 5L).subscriberCount()).isZero();
    assertThat(service.status(null, 5L).subscriberCount()).isZero();
    assertThat(service.status(1L, 5L).subscriberCount()).isEqualTo(4L);
  }

  @Test
  void aSeriesThatDoesNotExistShowsNoCount() {
    when(seriesRepository.findById(5L)).thenReturn(Optional.empty());

    assertThat(service.status(9L, 5L).subscriberCount()).isZero();
  }

  private static SeriesEntity series(long ownerId) {
    return new SeriesEntity(ownerId, "s", "S");
  }

  @Test
  void mySubscriptionsReturnsSeriesIds() {
    when(subscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of(1L, 2L, 3L));

    assertThat(service.mySubscriptions(9L)).containsExactly(1L, 2L, 3L);
  }
}
