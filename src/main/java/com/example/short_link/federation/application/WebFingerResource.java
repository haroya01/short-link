package com.example.short_link.federation.application;

import java.util.Locale;
import java.util.Optional;

// Mastodon asks with acct:name@domain; some servers drop the scheme or ask by actor URL.
public sealed interface WebFingerResource {

  record Username(String value) implements WebFingerResource {}

  record ActorId(String publicId) implements WebFingerResource {}

  record Instance() implements WebFingerResource {}

  static Optional<WebFingerResource> parse(
      String resource, String domain, String actorPrefix, String instanceId) {
    if (resource == null || resource.isBlank()) {
      return Optional.empty();
    }
    String value = resource.trim();
    if (value.equals(instanceId)) {
      return Optional.of(new Instance());
    }
    if (value.startsWith(actorPrefix)) {
      String publicId = value.substring(actorPrefix.length());
      return publicId.isEmpty() || publicId.contains("/")
          ? Optional.empty()
          : Optional.of(new ActorId(publicId));
    }
    if (value.regionMatches(true, 0, "acct:", 0, 5)) {
      value = value.substring(5);
    }
    if (value.startsWith("@")) {
      value = value.substring(1);
    }
    int at = value.lastIndexOf('@');
    if (at <= 0 || at == value.length() - 1) {
      return Optional.empty();
    }
    String host = value.substring(at + 1);
    if (!host.toLowerCase(Locale.ROOT).equals(domain.toLowerCase(Locale.ROOT))) {
      return Optional.empty();
    }
    String name = value.substring(0, at);
    return name.equalsIgnoreCase(domain)
        ? Optional.of(new Instance())
        : Optional.of(new Username(name));
  }
}
