package com.example.short_link.federation.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

public final class ActivityStreams {

  public static final String CONTEXT = "https://www.w3.org/ns/activitystreams";
  public static final String PUBLIC = CONTEXT + "#Public";

  // note.uri, note.remote_url and note_media.url are VARCHAR(512).
  public static final int MAX_URI = 512;

  private ActivityStreams() {}

  public static boolean sameHost(String uri, String other) {
    String host = RemoteActorParser.host(uri);
    return host != null && host.equals(RemoteActorParser.host(other));
  }

  // Mastodon's rule since CVE-2024-23832: activity+json, or ld+json naming this profile.
  public static boolean isDocumentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String[] parts = contentType.split(";");
    String type = parts[0].strip().toLowerCase(Locale.ROOT);
    if (type.equals("application/activity+json")) {
      return true;
    }
    if (!type.equals("application/ld+json")) {
      return false;
    }
    for (int i = 1; i < parts.length; i++) {
      String parameter = parts[i].strip();
      int equals = parameter.indexOf('=');
      if (equals < 0 || !parameter.substring(0, equals).strip().equalsIgnoreCase("profile")) {
        continue;
      }
      String value = parameter.substring(equals + 1).strip();
      if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value = value.substring(1, value.length() - 1);
      }
      if (Arrays.asList(value.strip().split("\\s+")).contains(CONTEXT)) {
        return true;
      }
    }
    return false;
  }

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

  // Or several of either: PeerTube attributes a video to its account and then its channel.
  public static List<String> idsOf(JsonNode node) {
    List<String> ids = new ArrayList<>();
    if (node == null || !node.isArray()) {
      String id = idOf(node);
      return id == null ? ids : List.of(id);
    }
    for (JsonNode item : node) {
      String id = idOf(item);
      if (id != null) {
        ids.add(id);
      }
    }
    return ids;
  }
}
