package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.MAX_URI;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

public final class RemoteEmojis {

  public static final int MAX = 50;
  private static final Pattern SHORTCODE = Pattern.compile("[A-Za-z0-9_]{2,128}");

  private RemoteEmojis() {}

  public static List<CustomEmoji> fromTags(JsonNode tags) {
    Map<String, CustomEmoji> kept = new LinkedHashMap<>();
    for (JsonNode tag : items(tags)) {
      if (kept.size() == MAX) {
        break;
      }
      String name = text(tag.get("name"));
      if (!"Emoji".equals(text(tag.get("type"))) || name == null) {
        continue;
      }
      String shortcode =
          name.length() > 2 && name.startsWith(":") && name.endsWith(":")
              ? name.substring(1, name.length() - 1)
              : name;
      keep(kept, shortcode, iconUrl(tag.get("icon")));
    }
    return List.copyOf(kept.values());
  }

  public static List<CustomEmoji> fromRest(JsonNode emojis) {
    Map<String, CustomEmoji> kept = new LinkedHashMap<>();
    for (JsonNode emoji : items(emojis)) {
      if (kept.size() == MAX) {
        break;
      }
      String still = text(emoji.get("static_url"));
      keep(
          kept,
          text(emoji.get("shortcode")),
          https(still) != null ? still : text(emoji.get("url")));
    }
    return List.copyOf(kept.values());
  }

  private static void keep(Map<String, CustomEmoji> kept, String shortcode, String url) {
    if (shortcode != null
        && SHORTCODE.matcher(shortcode).matches()
        && https(url) != null
        && !kept.containsKey(shortcode)) {
      kept.put(shortcode, new CustomEmoji(shortcode, url));
    }
  }

  private static String iconUrl(JsonNode icon) {
    JsonNode first = icon != null && icon.isArray() ? icon.get(0) : icon;
    return first != null && first.isObject() ? text(first.get("url")) : null;
  }

  private static String https(String url) {
    return url != null
            && url.startsWith("https://")
            && url.length() <= MAX_URI
            && RemoteActorParser.host(url) != null
        ? url
        : null;
  }

  private static Iterable<JsonNode> items(JsonNode node) {
    if (node == null || !(node.isArray() || node.isObject())) {
      return List.of();
    }
    return node.isArray() ? node : List.of(node);
  }
}
