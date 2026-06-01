package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.SeriesSubscriptionStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscribeSeriesUseCaseTest {

  @Mock private SeriesRepository seriesRepository;
  @Mock private SeriesSubscriptionRepository subscriptionRepository;

  private SubscribeSeriesUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SubscribeSeriesUseCase(seriesRepository, subscriptionRepository);
  }

  @Test
  void subscribeCreatesEdgeWhenNotYetSubscribed() {
    when(seriesRepository.findById(5L)).thenReturn(Optional.of(new SeriesEntity(1L, "s", "S")));
    when(subscriptionRepository.existsByUserIdAndSeriesId(9L, 5L)).thenReturn(false);
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(3L);

    SeriesSubscriptionStatus status = useCase.subscribe(9L, 5L);

    verify(subscriptionRepository).save(ArgumentMatchers.any(SeriesSubscriptionEntity.class));
    assertThat(status.subscribed()).isTrue();
    assertThat(status.subscriberCount()).isEqualTo(3L);
  }

  @Test
  void subscribeIsIdempotentWhenAlreadySubscribed() {
    when(seriesRepository.findById(5L)).thenReturn(Optional.of(new SeriesEntity(1L, "s", "S")));
    when(subscriptionRepository.existsByUserIdAndSeriesId(9L, 5L)).thenReturn(true);
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(1L);

    SeriesSubscriptionStatus status = useCase.subscribe(9L, 5L);

    verify(subscriptionRepository, never()).save(ArgumentMatchers.any());
    assertThat(status.subscribed()).isTrue();
  }

  @Test
  void subscribeThrowsWhenSeriesMissing() {
    when(seriesRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.subscribe(9L, 404L)).isInstanceOf(PostException.class);
    verify(subscriptionRepository, never()).save(ArgumentMatchers.any());
  }

  @Test
  void unsubscribeDeletesWhenSubscribed() {
    SeriesSubscriptionEntity edge = new SeriesSubscriptionEntity(9L, 5L);
    when(subscriptionRepository.findByUserIdAndSeriesId(9L, 5L)).thenReturn(Optional.of(edge));
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(0L);

    SeriesSubscriptionStatus status = useCase.unsubscribe(9L, 5L);

    verify(subscriptionRepository).delete(edge);
    assertThat(status.subscribed()).isFalse();
    assertThat(status.subscriberCount()).isZero();
  }

  @Test
  void unsubscribeIsNoOpWhenNotSubscribed() {
    when(subscriptionRepository.findByUserIdAndSeriesId(9L, 5L)).thenReturn(Optional.empty());
    when(subscriptionRepository.countBySeriesId(5L)).thenReturn(0L);

    SeriesSubscriptionStatus status = useCase.unsubscribe(9L, 5L);

    verify(subscriptionRepository, never()).delete(ArgumentMatchers.any());
    assertThat(status.subscribed()).isFalse();
  }
}
