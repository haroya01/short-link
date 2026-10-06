package com.example.short_link.federation.application.delivery;

import com.example.short_link.federation.application.FederationHttp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Mastodon's own practice: a 4xx (other than 408/429) means the inbox will never take this
// activity, so retrying only adds load. Network errors, 408, 429 and 5xx back off up to
// giveUpAfter.
public final class DeliveryPolicy {

  static final List<Duration> BACKOFF =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          Duration.ofHours(3),
          Duration.ofHours(6),
          Duration.ofHours(12),
          Duration.ofHours(24));

  private DeliveryPolicy() {}

  public static boolean retryable(FederationHttp.Result result) {
    return switch (result) {
      case FederationHttp.Result.Ok ok -> false;
      case FederationHttp.Result.Refused refused -> false;
      case FederationHttp.Result.Unreachable unreachable -> true;
      case FederationHttp.Result.Failed failed ->
          failed.status() == 408 || failed.status() == 429 || failed.status() >= 500;
    };
  }

  public static Optional<Instant> nextAttempt(
      int attempts, Instant createdAt, Instant now, Duration giveUpAfter) {
    if (attempts > BACKOFF.size()) {
      return Optional.empty();
    }
    Instant next = now.plus(BACKOFF.get(Math.max(0, attempts - 1)));
    return next.isAfter(createdAt.plus(giveUpAfter)) ? Optional.empty() : Optional.of(next);
  }
}
