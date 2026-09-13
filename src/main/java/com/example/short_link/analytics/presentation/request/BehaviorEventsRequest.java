package com.example.short_link.analytics.presentation.request;

import java.util.List;

public record BehaviorEventsRequest(String sessionId, List<Item> events) {

  public record Item(
      String name,
      Long postId,
      String targetType,
      String targetId,
      Integer depthPct,
      Long dwellMs) {}
}
