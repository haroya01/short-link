package com.example.short_link.federation.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DeliveryQueueTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

  @Mock private FederationDeliveryRepository deliveries;

  private DeliveryQueue queue() {
    return new DeliveryQueue(deliveries, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void eachInboxGetsOneRowAndRepeatsAreDropped() {
    when(deliveries.existsByDedupeKey(DeliveryQueue.dedupeKey("act-1", "https://a.example/inbox")))
        .thenReturn(true);
    when(deliveries.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    int added =
        queue()
            .enqueue(
                7L,
                "act-1",
                "{}",
                List.of(
                    "https://a.example/inbox",
                    "https://B.example/inbox",
                    "https://B.example/inbox",
                    "not a url"));

    assertThat(added).isEqualTo(1);
    ArgumentCaptor<FederationDeliveryEntity> saved =
        ArgumentCaptor.forClass(FederationDeliveryEntity.class);
    verify(deliveries, times(1)).saveAndFlush(saved.capture());
    FederationDeliveryEntity row = saved.getValue();
    assertThat(row.getInbox()).isEqualTo("https://B.example/inbox");
    assertThat(row.getInboxHost()).isEqualTo("b.example");
    assertThat(row.getSignerUserId()).isEqualTo(7L);
    assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
    assertThat(row.getDedupeKey()).hasSize(64);
  }

  @Test
  void aConcurrentEnqueueIsNotAnError() {
    when(deliveries.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThat(queue().enqueue(null, "act-2", "{}", List.of("https://a.example/inbox"))).isZero();
  }

  @Test
  void dedupeKeyDependsOnActivityAndInbox() {
    assertThat(DeliveryQueue.dedupeKey("a", "x")).isEqualTo(DeliveryQueue.dedupeKey("a", "x"));
    assertThat(DeliveryQueue.dedupeKey("a", "x")).isNotEqualTo(DeliveryQueue.dedupeKey("a", "y"));
    assertThat(DeliveryQueue.dedupeKey("ab", "c")).isNotEqualTo(DeliveryQueue.dedupeKey("a", "bc"));
  }
}
