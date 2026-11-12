package com.example.short_link.link.webhook.scheduler;

import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import com.example.short_link.link.webhook.domain.WebhookRetryPolicy;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class WebhookDeliveryLedger {

  static final int CLAIM_SIZE = 100;
  static final Duration LEASE = Duration.ofMinutes(2);
  static final Duration RETENTION = Duration.ofDays(7);

  private final LinkWebhookDeliveryRepository deliveries;

  @Transactional
  public List<ClaimedDelivery> claim(Instant now) {
    return deliveries.lockDue(now, CLAIM_SIZE).stream()
        .map(
            delivery -> {
              delivery.lease(now.plus(LEASE));
              return new ClaimedDelivery(
                  delivery.getId(),
                  delivery.getHookId(),
                  delivery.getPayload(),
                  delivery.getAttempts());
            })
        .toList();
  }

  @Transactional
  public void record(List<ClaimedDelivery> claimed, WebhookSender.Result result, Instant now) {
    for (LinkWebhookDeliveryEntity delivery : rows(claimed)) {
      if (result.ok()) {
        delivery.delivered(result.statusCode());
        continue;
      }
      var next =
          WebhookRetryPolicy.retryable(result)
              ? WebhookRetryPolicy.nextAttempt(delivery.getAttempts(), now)
              : Optional.<Instant>empty();
      if (next.isPresent()) {
        delivery.retryAt(next.get(), result.statusCode(), result.error());
      } else {
        delivery.giveUp(result.statusCode(), result.error());
      }
    }
  }

  @Transactional
  public void giveUp(List<ClaimedDelivery> claimed, String reason) {
    rows(claimed).forEach(delivery -> delivery.giveUp(null, reason));
  }

  @Transactional
  public int purge(Instant now) {
    return deliveries.deleteFinishedBefore(now.minus(RETENTION));
  }

  private List<LinkWebhookDeliveryEntity> rows(List<ClaimedDelivery> claimed) {
    return deliveries.findAllById(claimed.stream().map(ClaimedDelivery::id).toList());
  }

  record ClaimedDelivery(Long id, Long hookId, String payload, int attempts) {}
}
