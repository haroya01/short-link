package com.example.short_link.live.application;

import java.util.List;

public sealed interface LiveEvent {

  record Ready(String scope, long scopeId, String cursor, String streamToken, long retryMs)
      implements LiveEvent {}

  record Bye(String reason, long retryMs) implements LiveEvent {}

  record Changed(
      String scope, long scopeId, List<Item> items, boolean more, String cursor, String at)
      implements LiveEvent {}

  record Revoked(String scope, long scopeId) implements LiveEvent {}

  record Busy(int retryAfterSeconds) implements LiveEvent {}

  record Heartbeat() implements LiveEvent {}

  record Item(String kind, long id) {}
}
