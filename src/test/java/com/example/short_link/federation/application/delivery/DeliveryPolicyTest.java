package com.example.short_link.federation.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.application.FederationHttp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryPolicyTest {

  private static final Instant T0 = Instant.parse("2026-10-06T00:00:00Z");

  @Test
  void onlyTransientFailuresAreRetried() {
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Unreachable("x"))).isTrue();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(500, "e"))).isTrue();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(503, "e"))).isTrue();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(429, "e"))).isTrue();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(408, "e"))).isTrue();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(401, "e"))).isFalse();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(404, "e"))).isFalse();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Failed(410, "e"))).isFalse();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Refused("x"))).isFalse();
    assertThat(DeliveryPolicy.retryable(new FederationHttp.Result.Ok(202, new byte[0]))).isFalse();
  }

  @Test
  void backsOffThenGivesUpByAttemptsOrAge() {
    Duration twoDays = Duration.ofDays(2);
    assertThat(DeliveryPolicy.nextAttempt(1, T0, T0, twoDays)).contains(T0.plusSeconds(60));
    assertThat(DeliveryPolicy.nextAttempt(2, T0, T0, twoDays)).contains(T0.plusSeconds(300));
    assertThat(DeliveryPolicy.nextAttempt(8, T0, T0, twoDays))
        .contains(T0.plus(Duration.ofHours(24)));
    assertThat(DeliveryPolicy.nextAttempt(9, T0, T0, twoDays)).isEmpty();
    Instant late = T0.plus(Duration.ofHours(40));
    assertThat(DeliveryPolicy.nextAttempt(7, T0, late, twoDays)).isEmpty();
    assertThat(DeliveryPolicy.nextAttempt(0, T0, T0, twoDays)).contains(T0.plusSeconds(60));
  }
}
