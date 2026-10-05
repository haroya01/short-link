package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.delivery.ClaimedDelivery;
import com.example.short_link.federation.application.delivery.DeliveryLedger;
import com.example.short_link.federation.application.delivery.DeliveryProperties;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Sends one claimed batch per tick on the scheduler thread, so delivery adds no threads beside the
// request pool. fixedDelay keeps ticks from overlapping.
@Slf4j
@Component
public class DeliveryWorker {

  private final DeliveryLedger ledger;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final DeliveryProperties props;
  private final MeterRegistry meters;
  private final Clock clock;

  @Autowired
  public DeliveryWorker(
      DeliveryLedger ledger,
      FederationHttp http,
      SigningKeys signingKeys,
      DeliveryProperties props,
      MeterRegistry meters) {
    this(ledger, http, signingKeys, props, meters, Clock.systemUTC());
  }

  DeliveryWorker(
      DeliveryLedger ledger,
      FederationHttp http,
      SigningKeys signingKeys,
      DeliveryProperties props,
      MeterRegistry meters,
      Clock clock) {
    this.ledger = ledger;
    this.http = http;
    this.signingKeys = signingKeys;
    this.props = props;
    this.meters = meters;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${short-link.federation.delivery.interval:PT5S}")
  public void deliverDue() {
    if (!props.enabled()) {
      return;
    }
    List<ClaimedDelivery> batch = ledger.claim(clock.instant());
    for (ClaimedDelivery delivery : batch) {
      FederationHttp.Result result = send(delivery);
      String outcome = ledger.record(delivery, result, clock.instant());
      meters.counter("federation.delivery", "outcome", outcome).increment();
      if (!"delivered".equals(outcome)) {
        log.info(
            "federation delivery outcome={} attempts={} inbox={} result={}",
            outcome,
            delivery.attempts(),
            delivery.inbox(),
            result);
      }
    }
  }

  @Scheduled(
      cron = "${short-link.federation.delivery.purge-cron:0 20 4 * * *}",
      zone = "Asia/Seoul")
  public void purgeFinished() {
    if (!props.enabled()) {
      return;
    }
    int purged = ledger.purge(clock.instant());
    if (purged > 0) {
      log.info("federation delivery purged={}", purged);
    }
  }

  private FederationHttp.Result send(ClaimedDelivery delivery) {
    Optional<Signer> signer =
        delivery.signerUserId() == null
            ? Optional.of(signingKeys.forInstance())
            : signingKeys.forUser(delivery.signerUserId());
    if (signer.isEmpty()) {
      return new FederationHttp.Result.Refused("signer gone");
    }
    try {
      return http.post(
          URI.create(delivery.inbox()),
          delivery.body().getBytes(StandardCharsets.UTF_8),
          signer.get());
    } catch (IllegalArgumentException badInbox) {
      return new FederationHttp.Result.Refused("bad inbox");
    }
  }
}
