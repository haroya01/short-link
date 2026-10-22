package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.RemoteActorDocument;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

// Accepts only an actor whose id is the address it was fetched from and whose inbox and key live on
// that host, so neither another server nor another document on the same server can publish a key
// or inbox for an account.
public final class RemoteActorParser {

  private static final Set<String> ACTOR_TYPES =
      Set.of("Person", "Service", "Application", "Group", "Organization");

  private RemoteActorParser() {}

  public static Optional<String> keyOwner(JsonNode document, String keyId) {
    JsonNode key = publicKey(document, keyId);
    if (key == null) {
      return Optional.empty();
    }
    String owner = text(key.get("owner"));
    if (owner == null && "Key".equals(text(document.get("type")))) {
      owner = text(document.get("owner"));
    }
    return Optional.ofNullable(owner);
  }

  public static Optional<RemoteActorDocument> parse(
      JsonNode document, URI fetchedFrom, String keyId) {
    String id = text(document.get("id"));
    String type = text(document.get("type"));
    String inbox = text(document.get("inbox"));
    if (id == null || inbox == null || type == null || !ACTOR_TYPES.contains(type)) {
      return Optional.empty();
    }
    String host = host(fetchedFrom);
    if (host == null
        || !withoutFragment(id).equals(withoutFragment(fetchedFrom.toString()))
        || !host.equals(host(inbox))) {
      return Optional.empty();
    }
    JsonNode key = publicKey(document, keyId);
    if (key == null) {
      return Optional.empty();
    }
    String resolvedKeyId = text(key.get("id"));
    String owner = text(key.get("owner"));
    String pem = text(key.get("publicKeyPem"));
    if (resolvedKeyId == null
        || !host.equals(host(resolvedKeyId))
        || (owner != null && !owner.equals(id))
        || pem == null
        || !pem.contains("-----BEGIN PUBLIC KEY-----")) {
      return Optional.empty();
    }
    JsonNode endpoints = document.get("endpoints");
    String sharedInbox = endpoints == null ? null : text(endpoints.get("sharedInbox"));
    if (sharedInbox != null && !host.equals(host(sharedInbox))) {
      sharedInbox = null;
    }
    return Optional.of(
        new RemoteActorDocument(
            id,
            resolvedKeyId,
            pem,
            inbox,
            sharedInbox,
            text(document.get("preferredUsername")),
            host,
            link(document.get("url")),
            text(document.get("name")),
            link(document.get("icon")),
            flag(document.get("discoverable")),
            flag(document.get("indexable"))));
  }

  private static JsonNode publicKey(JsonNode document, String keyId) {
    if ("Key".equals(text(document.get("type")))) {
      return document;
    }
    JsonNode key = document.get("publicKey");
    if (key == null) {
      return null;
    }
    if (key.isArray()) {
      for (JsonNode candidate : key) {
        if (keyId == null || keyId.equals(text(candidate.get("id")))) {
          return candidate;
        }
      }
      return null;
    }
    return keyId == null || keyId.equals(text(key.get("id"))) ? key : null;
  }

  private static String link(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isArray()) {
      return node.isEmpty() ? null : link(node.get(0));
    }
    if (node.isObject()) {
      String url = text(node.get("url"));
      return url != null ? url : text(node.get("href"));
    }
    return text(node);
  }

  private static boolean flag(JsonNode node) {
    return node != null && node.isBoolean() && node.asBoolean();
  }

  private static String text(JsonNode node) {
    if (node == null || !node.isString()) {
      return null;
    }
    String value = node.asString().strip();
    return value.isEmpty() ? null : value;
  }

  public static Optional<String> id(JsonNode document) {
    return Optional.ofNullable(text(document.get("id")));
  }

  private static String withoutFragment(String uri) {
    int hash = uri.indexOf('#');
    return hash < 0 ? uri : uri.substring(0, hash);
  }

  public static String host(String url) {
    try {
      return host(URI.create(url));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String host(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null || uri.getHost() == null) {
      return null;
    }
    String s = scheme.toLowerCase(Locale.ROOT);
    if (!s.equals("https") && !s.equals("http")) {
      return null;
    }
    return uri.getPort() == -1
        ? uri.getHost().toLowerCase(Locale.ROOT)
        : uri.getHost().toLowerCase(Locale.ROOT) + ":" + uri.getPort();
  }
}
