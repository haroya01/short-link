package com.example.short_link.link.health.application;

import com.example.short_link.link.health.domain.DestinationFailure;

public record DestinationCheck(Outcome outcome, DestinationFailure failure, Integer httpStatus) {

  public enum Outcome {
    HEALTHY,
    BROKEN,
    INCONCLUSIVE
  }

  static DestinationCheck healthy(int status) {
    return new DestinationCheck(Outcome.HEALTHY, null, status);
  }

  static DestinationCheck broken(DestinationFailure failure, Integer status) {
    return new DestinationCheck(Outcome.BROKEN, failure, status);
  }

  static DestinationCheck inconclusive() {
    return new DestinationCheck(Outcome.INCONCLUSIVE, null, null);
  }
}
