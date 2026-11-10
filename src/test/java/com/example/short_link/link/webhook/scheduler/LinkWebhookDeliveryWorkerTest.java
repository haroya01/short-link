package com.example.short_link.link.webhook.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.link.webhook.scheduler.WebhookDeliveryLedger.ClaimedDelivery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LinkWebhookDeliveryWorkerTest {

  private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

  private final WebhookDeliveryLedger ledger = mock(WebhookDeliveryLedger.class);
  private final LinkWebhookRepository hooks = mock(LinkWebhookRepository.class);
  private final WebhookHttpDeliveryClient client = mock(WebhookHttpDeliveryClient.class);
  private final LinkWebhookDeliveryWorker worker =
      new LinkWebhookDeliveryWorker(
          ledger,
          hooks,
          client,
          JsonMapper.builder().build(),
          new SimpleMeterRegistry(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aHookThatFailsToLoadLeavesItsRowsForTheNextLeaseAndTheOthersStillGo() {
    ClaimedDelivery broken = new ClaimedDelivery(1L, 7L, "{\"type\":\"click\"}", 1);
    ClaimedDelivery gone = new ClaimedDelivery(2L, 8L, "{\"type\":\"click\"}", 1);
    when(ledger.claim(NOW)).thenReturn(List.of(broken, gone));
    when(hooks.findById(7L)).thenThrow(new IllegalStateException("db hiccup"));
    when(hooks.findById(8L)).thenReturn(Optional.empty());

    worker.deliverDue();

    verify(ledger, never()).record(anyList(), any(), any());
    verify(ledger).giveUp(List.of(gone), "webhook disabled");
    verify(client, never()).deliver(any(), any());
  }

  @Test
  void nothingDueSendsNothing() {
    when(ledger.claim(NOW)).thenReturn(List.of());

    worker.deliverDue();

    verify(hooks, never()).findById(any());
  }

  @Test
  void finishedRowsArePurgedOnTheDailyRun() {
    when(ledger.purge(NOW)).thenReturn(3);

    worker.purgeFinished();

    verify(ledger).purge(NOW);
  }
}
