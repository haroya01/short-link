package com.example.short_link.link.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.common.webhook.WebhookSender.Outcome;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WebhookRetryPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

  @ParameterizedTest(name = "{0} {1} -> {2}")
  @CsvSource({
    "OK,200,false",
    "BLOCKED,,false",
    "SIGN_ERROR,,false",
    "EXCEPTION,,true",
    "NON_2XX,500,true",
    "NON_2XX,503,true",
    "NON_2XX,408,true",
    "NON_2XX,425,true",
    "NON_2XX,429,true",
    "NON_2XX,,true",
    "NON_2XX,400,false",
    "NON_2XX,404,false",
    "NON_2XX,410,false"
  })
  void onlyAFailureTheReceiverMayRecoverFromIsTriedAgain(
      Outcome outcome, Integer status, boolean retryable) {
    assertThat(WebhookRetryPolicy.retryable(new WebhookSender.Result(outcome, status, null)))
        .isEqualTo(retryable);
  }

  @Test
  void theWaitDoublesFromThirtySecondsAndStopsAtTheLastAttempt() {
    assertThat(WebhookRetryPolicy.nextAttempt(1, NOW)).contains(NOW.plusSeconds(30));
    assertThat(WebhookRetryPolicy.nextAttempt(2, NOW)).contains(NOW.plusSeconds(60));
    assertThat(WebhookRetryPolicy.nextAttempt(5, NOW)).contains(NOW.plusSeconds(480));
    assertThat(WebhookRetryPolicy.nextAttempt(WebhookRetryPolicy.MAX_ATTEMPTS, NOW)).isEmpty();
  }

  @Test
  void theWaitNeverPassesAnHour() {
    assertThat(WebhookRetryPolicy.nextAttempt(0, NOW)).contains(NOW.plusSeconds(30));
    assertThat(
            Duration.between(
                NOW,
                WebhookRetryPolicy.nextAttempt(WebhookRetryPolicy.MAX_ATTEMPTS - 1, NOW).get()))
        .isLessThanOrEqualTo(Duration.ofHours(1));
  }
}
