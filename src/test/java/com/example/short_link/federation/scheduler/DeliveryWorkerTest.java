package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.delivery.ClaimedDelivery;
import com.example.short_link.federation.application.delivery.DeliveryLedger;
import com.example.short_link.federation.application.delivery.DeliveryProperties;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryWorkerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

  @Mock private DeliveryLedger ledger;
  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private DeliveryWorker worker(boolean enabled) {
    return new DeliveryWorker(
        ledger,
        http,
        signingKeys,
        new DeliveryProperties(enabled, 5, null, null, null),
        meters,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void signsWithTheUserOrTheInstanceAndRecordsEachOutcome() {
    Signer user = new Signer("u#main-key", null);
    Signer instance = new Signer("i#main-key", null);
    ClaimedDelivery byUser = new ClaimedDelivery(1L, "https://a.example/inbox", 7L, "{}", 1, NOW);
    ClaimedDelivery byInstance =
        new ClaimedDelivery(2L, "https://b.example/inbox", null, "{}", 1, NOW);
    when(ledger.claim(NOW)).thenReturn(List.of(byUser, byInstance));
    when(signingKeys.forUser(7L)).thenReturn(Optional.of(user));
    when(signingKeys.forInstance()).thenReturn(instance);
    var ok = new FederationHttp.Result.Ok(202, new byte[0]);
    when(http.post(eq(URI.create("https://a.example/inbox")), any(), eq(user))).thenReturn(ok);
    when(http.post(eq(URI.create("https://b.example/inbox")), any(), eq(instance)))
        .thenReturn(new FederationHttp.Result.Failed(500, "HTTP 500"));
    when(ledger.record(byUser, ok, NOW)).thenReturn("delivered");
    when(ledger.record(eq(byInstance), any(), eq(NOW))).thenReturn("retry");

    worker(true).deliverDue();

    assertThat(meters.counter("federation.delivery", "outcome", "delivered").count()).isEqualTo(1);
    assertThat(meters.counter("federation.delivery", "outcome", "retry").count()).isEqualTo(1);
  }

  @Test
  void aDeletedSignerOrABrokenInboxIsRefusedWithoutSending() {
    ClaimedDelivery gone = new ClaimedDelivery(1L, "https://a.example/inbox", 9L, "{}", 1, NOW);
    ClaimedDelivery broken = new ClaimedDelivery(2L, "not a uri", null, "{}", 1, NOW);
    when(ledger.claim(NOW)).thenReturn(List.of(gone, broken));
    when(signingKeys.forUser(9L)).thenReturn(Optional.empty());
    when(signingKeys.forInstance()).thenReturn(new Signer("i", null));
    when(ledger.record(any(), any(), eq(NOW))).thenReturn("gave_up");

    worker(true).deliverDue();

    verify(ledger).record(gone, new FederationHttp.Result.Refused("signer gone"), NOW);
    verify(ledger).record(broken, new FederationHttp.Result.Refused("bad inbox"), NOW);
    verify(http, never()).post(any(), any(), any());
  }

  @Test
  void aDisabledWorkerDoesNothing() {
    worker(false).deliverDue();
    worker(false).purgeFinished();

    verifyNoInteractions(ledger, http, signingKeys);
  }

  @Test
  void purgeRunsWhenEnabled() {
    when(ledger.purge(NOW)).thenReturn(0).thenReturn(4);

    worker(true).purgeFinished();
    worker(true).purgeFinished();

    verify(ledger, org.mockito.Mockito.times(2)).purge(NOW);
  }
}
