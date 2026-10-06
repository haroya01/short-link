package com.example.short_link.federation.application;

import tools.jackson.databind.JsonNode;

public final class ActivityStreams {

  public static final String CONTEXT = "https://www.w3.org/ns/activitystreams";
  public static final String PUBLIC = CONTEXT + "#Public";

  private ActivityStreams() {}

  public static String text(JsonNode node) {
    if (node == null || !node.isString()) {
      return null;
    }
    String value = node.asString().strip();
    return value.isEmpty() ? null : value;
  }

  // Activity properties may hold either a bare id or the embedded object.
  public static String idOf(JsonNode node) {
    if (node != null && node.isObject()) {
      return text(node.get("id"));
    }
    return text(node);
  }
}
