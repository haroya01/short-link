package com.example.short_link.federation.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.domain.DeliveryStatus;
import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeliveryLedgerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

  @Mock private FederationDeliveryRepository deliveries;

  private final DeliveryProperties props = new DeliveryProperties(true, 5, null, null, null);

  private DeliveryLedger ledger() {
    return new DeliveryLedger(deliveries, props);
  }

  private static FederationDeliveryEntity row(long id) {
    FederationDeliveryEntity row =
        new FederationDeliveryEntity(
            "k" + id, "https://a.example/inbox", "a.example", 7L, "act", "{}", NOW);
    ReflectionTestUtils.setField(row, "id", id);
    ReflectionTestUtils.setField(row, "createdAt", NOW);
    return row;
  }

  @Test
  void claimLeasesEachDueRowAndCountsTheAttempt() {
    FederationDeliveryEntity row = row(1);
    when(deliveries.lockDue(NOW, 5)).thenReturn(List.of(row));

    List<ClaimedDelivery> claimed = ledger().claim(NOW);

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).attempts()).isEqualTo(1);
    assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
  }

  @Test
  void outcomesMoveTheRowToItsNextState() {
    FederationDeliveryEntity row = row(1);
    when(deliveries.findById(1L)).thenReturn(Optional.of(row));
    ClaimedDelivery first = new ClaimedDelivery(1L, row.getInbox(), 7L, "{}", 1, NOW);

    assertThat(ledger().record(first, new FederationHttp.Result.Failed(503, "HTTP 503"), NOW))
        .isEqualTo("retry");
    assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(row.getLastStatus()).isEqualTo(503);

    assertThat(ledger().record(first, new FederationHttp.Result.Ok(202, new byte[0]), NOW))
        .isEqualTo("delivered");
    assertThat(row.getStatus()).isEqualTo(DeliveryStatus.DONE);
    assertThat(row.getLastError()).isNull();

    assertThat(ledger().record(first, new FederationHttp.Result.Failed(410, "HTTP 410"), NOW))
        .isEqualTo("gave_up");
    assertThat(row.getStatus()).isEqualTo(DeliveryStatus.GAVE_UP);

    ClaimedDelivery exhausted = new ClaimedDelivery(1L, row.getInbox(), 7L, "{}", 9, NOW);
    assertThat(ledger().record(exhausted, new FederationHttp.Result.Unreachable("x"), NOW))
        .isEqualTo("gave_up");
    assertThat(row.getLastError()).isEqualTo("unreachable: x");

    assertThat(ledger().record(first, new FederationHttp.Result.Refused("y".repeat(300)), NOW))
        .isEqualTo("gave_up");
    assertThat(row.getLastError()).hasSize(255);
  }

  @Test
  void aRowDeletedMeanwhileIsReportedGone() {
    when(deliveries.findById(2L)).thenReturn(Optional.empty());

    assertThat(
            ledger()
                .record(
                    new ClaimedDelivery(2L, "https://a.example/inbox", null, "{}", 1, NOW),
                    new FederationHttp.Result.Ok(200, new byte[0]),
                    NOW))
        .isEqualTo("gone");
  }

  @Test
  void purgeKeepsAWeekOfFinishedRows() {
    when(deliveries.deleteFinishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

    assertThat(ledger().purge(NOW)).isEqualTo(3);
  }

  @Test
  void propertiesFallBackToSafeDefaults() {
    DeliveryProperties defaults = new DeliveryProperties(null, 0, null, null, null);
    assertThat(defaults.enabled()).isTrue();
    assertThat(defaults.batchSize()).isEqualTo(20);
    assertThat(defaults.giveUpAfter()).isEqualTo(Duration.ofDays(2));
    assertThat(new DeliveryProperties(false, 1, null, null, null).enabled()).isFalse();
  }
}
