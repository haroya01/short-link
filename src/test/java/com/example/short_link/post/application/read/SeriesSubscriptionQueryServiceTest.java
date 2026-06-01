package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeriesSubscriptionQueryServiceTest {

  @Mock private SeriesSubscriptionRepository subscriptionRepository;

  private SeriesSubscriptionQueryService service;

  @BeforeEach
  void setUp() {
    service = new SeriesSubscriptionQueryService(subscriptionRepository);
  }

  @Test
  void statusReflectsSubscriptionAndCount() {
    when(subscriptionRepository.existsByUserIdAndSeriesId(9L, 5L)).thenReturn(true);
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(4L);

    SeriesSubscriptionStatus status = service.status(9L, 5L);

    assertThat(status.subscribed()).isTrue();
    assertThat(status.subscriberCount()).isEqualTo(4L);
  }

  @Test
  void anonymousViewerIsNeverSubscribedButStillSeesCount() {
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(4L);

    SeriesSubscriptionStatus status = service.status(null, 5L);

    assertThat(status.subscribed()).isFalse();
    assertThat(status.subscriberCount()).isEqualTo(4L);
  }

  @Test
  void mySubscriptionsReturnsSeriesIds() {
    when(subscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of(1L, 2L, 3L));

    assertThat(service.mySubscriptions(9L)).containsExactly(1L, 2L, 3L);
  }
}
