package com.example.short_link.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Documents shaped as Mastodon, the WordPress ActivityPub plugin, Lemmy and PeerTube serve them,
// with made-up accounts and hosts.
public final class FederationFixtures {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private FederationFixtures() {}

  public static String text(String name) {
    try (InputStream in = FederationFixtures.class.getResourceAsStream("/federation/" + name)) {
      if (in == null) {
        throw new IllegalArgumentException("no fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public static JsonNode json(String name) {
    return JSON.readTree(text(name));
  }
}
