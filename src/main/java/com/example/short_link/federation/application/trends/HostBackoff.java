package com.example.short_link.federation.application.trends;

import java.util.HashMap;
import java.util.Map;

final class HostBackoff {

  private static final int MAX_FAILURES = 4;

  private final Map<String, Backoff> backoffs = new HashMap<>();

  private record Backoff(int failures, int skipsLeft) {}

  boolean waiting(String server) {
    Backoff backoff = backoffs.get(server);
    if (backoff == null || backoff.skipsLeft() == 0) {
      return false;
    }
    backoffs.put(server, new Backoff(backoff.failures(), backoff.skipsLeft() - 1));
    return true;
  }

  void failed(String server) {
    int failures =
        Math.min(backoffs.getOrDefault(server, new Backoff(0, 0)).failures() + 1, MAX_FAILURES);
    backoffs.put(server, new Backoff(failures, 1 << (failures - 1)));
  }

  void answered(String server) {
    backoffs.remove(server);
  }
}
