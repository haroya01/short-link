package com.example.short_link.common.pow;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Maps verification outcomes to the established operational metric names and result tags. */
@Component
@RequiredArgsConstructor
final class PowMetrics {
  private final MeterRegistry registry;

  void challengeIssued() {
    registry.counter("pow.challenge.issued").increment();
  }

  void verificationCompleted(PowService.VerificationResult result) {
    String tag =
        switch (result) {
          case MISSING -> "missing";
          case BAD_PROOF -> "bad_proof";
          case UNKNOWN_OR_USED -> "unknown_or_used";
          case ACCEPTED -> "ok";
        };
    registry.counter("pow.verify", "result", tag).increment();
  }
}
