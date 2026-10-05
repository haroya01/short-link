package com.example.short_link.federation.application.delivery;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Claims and outcomes run in their own short transactions; the HTTP send happens between them with
// no transaction or connection held.
@Service
@RequiredArgsConstructor
public class DeliveryLedger {

  private final FederationDeliveryRepository deliveries;
  private final DeliveryProperties props;

  @Transactional
  public List<ClaimedDelivery> claim(Instant now) {
    Instant leaseUntil = now.plus(props.lease());
    return deliveries.lockDue(now, props.batchSize()).stream()
        .map(
            delivery -> {
              delivery.lease(leaseUntil);
              return new ClaimedDelivery(
                  delivery.getId(),
                  delivery.getInbox(),
                  delivery.getSignerUserId(),
                  delivery.getBody(),
                  delivery.getAttempts(),
                  delivery.getCreatedAt());
            })
        .toList();
  }

  @Transactional
  public String record(ClaimedDelivery claimed, FederationHttp.Result result, Instant now) {
    FederationDeliveryEntity delivery = deliveries.findById(claimed.id()).orElse(null);
    if (delivery == null) {
      return "gone";
    }
    if (result instanceof FederationHttp.Result.Ok ok) {
      delivery.delivered(ok.status());
      return "delivered";
    }
    Integer status = result instanceof FederationHttp.Result.Failed failed ? failed.status() : null;
    String error = describe(result);
    if (DeliveryPolicy.retryable(result)) {
      var next =
          DeliveryPolicy.nextAttempt(
              claimed.attempts(), claimed.createdAt(), now, props.giveUpAfter());
      if (next.isPresent()) {
        delivery.retryAt(next.get(), status, error);
        return "retry";
      }
    }
    delivery.giveUp(status, error);
    return "gave_up";
  }

  @Transactional
  public int purge(Instant now) {
    return deliveries.deleteFinishedBefore(now.minus(props.retention()));
  }

  private static String describe(FederationHttp.Result result) {
    return switch (result) {
      case FederationHttp.Result.Ok ok -> null;
      case FederationHttp.Result.Failed failed -> failed.reason();
      case FederationHttp.Result.Refused refused -> "refused: " + refused.reason();
      case FederationHttp.Result.Unreachable unreachable -> "unreachable: " + unreachable.reason();
    };
  }
}
