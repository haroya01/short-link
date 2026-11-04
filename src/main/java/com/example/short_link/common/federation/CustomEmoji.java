package com.example.short_link.common.federation;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record CustomEmoji(String shortcode, String url) {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  public static String column(List<CustomEmoji> emojis) {
    return emojis == null || emojis.isEmpty() ? null : JSON.writeValueAsString(emojis);
  }

  public static List<CustomEmoji> fromColumn(Object column) {
    String json = column == null ? null : column.toString();
    if (json == null || json.isBlank()) {
      return List.of();
    }
    JsonNode items;
    try {
      items = JSON.readTree(json);
    } catch (JacksonException malformed) {
      return List.of();
    }
    List<CustomEmoji> emojis = new ArrayList<>();
    for (JsonNode item : items) {
      String shortcode = item.path("shortcode").asString(null);
      String url = item.path("url").asString(null);
      if (shortcode != null && url != null) {
        emojis.add(new CustomEmoji(shortcode, url));
      }
    }
    return List.copyOf(emojis);
  }
}
