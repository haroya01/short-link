package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class RemoteActorParserTest {

  private final JsonMapper json = JsonMapper.builder().build();

  private JsonNode read(String body) {
    return json.readTree(body);
  }

  @Test
  void readsAMastodonActor() {
    var actor =
        RemoteActorParser.parse(
                read(RemoteActorFixtures.MASTODON),
                URI.create("https://mastodon.example/users/alice"),
                "https://mastodon.example/users/alice#main-key")
            .orElseThrow();

    assertThat(RemoteActorFixtures.pemIsValid()).isTrue();
    assertThat(actor.actorUri()).isEqualTo("https://mastodon.example/users/alice");
    assertThat(actor.keyId()).isEqualTo("https://mastodon.example/users/alice#main-key");
    assertThat(actor.inbox()).isEqualTo("https://mastodon.example/users/alice/inbox");
    assertThat(actor.sharedInbox()).isEqualTo("https://mastodon.example/inbox");
    assertThat(actor.username()).isEqualTo("alice");
    assertThat(actor.domain()).isEqualTo("mastodon.example");
    assertThat(actor.profileUrl()).isEqualTo("https://mastodon.example/@alice");
    assertThat(actor.displayName()).isEqualTo("Alice");
    assertThat(actor.avatarUrl()).isEqualTo("https://files.mastodon.example/a.png");
  }

  @Test
  void aGoToSocialKeyStubIsNotAnActorButNamesItsOwner() {
    JsonNode stub = read(RemoteActorFixtures.GOTOSOCIAL_KEY_STUB);
    String keyId = "https://gts.example/users/bob/main-key";

    assertThat(RemoteActorParser.parse(stub, URI.create(keyId), keyId)).isEmpty();
    assertThat(RemoteActorParser.keyOwner(stub, keyId)).contains("https://gts.example/users/bob");

    var actor =
        RemoteActorParser.parse(
                read(RemoteActorFixtures.GOTOSOCIAL_ACTOR),
                URI.create("https://gts.example/users/bob"),
                keyId)
            .orElseThrow();
    assertThat(actor.sharedInbox()).isNull();
    assertThat(actor.avatarUrl()).isEqualTo("https://gts.example/media/b.png");
  }

  @Test
  void refusesAnActorThatSpeaksForAnotherHost() {
    URI from = URI.create("https://mastodon.example/users/alice");
    String keyId = "https://mastodon.example/users/alice#main-key";

    ObjectNode otherId = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    otherId.put("id", "https://evil.example/users/alice");
    assertThat(RemoteActorParser.parse(otherId, from, keyId)).isEmpty();

    ObjectNode otherInbox = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    otherInbox.put("inbox", "https://evil.example/inbox");
    assertThat(RemoteActorParser.parse(otherInbox, from, keyId)).isEmpty();

    ObjectNode otherOwner = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    ((ObjectNode) otherOwner.get("publicKey")).put("owner", "https://mastodon.example/users/eve");
    assertThat(RemoteActorParser.parse(otherOwner, from, keyId)).isEmpty();

    ObjectNode badPem = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    ((ObjectNode) badPem.get("publicKey")).put("publicKeyPem", "nope");
    assertThat(RemoteActorParser.parse(badPem, from, keyId)).isEmpty();

    ObjectNode foreignShared = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    ((ObjectNode) foreignShared.get("endpoints")).put("sharedInbox", "https://evil.example/inbox");
    assertThat(RemoteActorParser.parse(foreignShared, from, keyId).orElseThrow().sharedInbox())
        .isNull();

    assertThat(
            RemoteActorParser.parse(
                read(RemoteActorFixtures.MASTODON), from, "https://mastodon.example/other#key"))
        .isEmpty();
  }

  @Test
  void refusesNonActorsAndBrokenDocuments() {
    URI from = URI.create("https://mastodon.example/users/alice");
    ObjectNode note = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    note.put("type", "Note");
    assertThat(RemoteActorParser.parse(note, from, null)).isEmpty();
    assertThat(RemoteActorParser.parse(read("{}"), from, null)).isEmpty();
    assertThat(
            RemoteActorParser.parse(read("{\"id\":1,\"inbox\":2,\"type\":\"Person\"}"), from, null))
        .isEmpty();
    assertThat(RemoteActorParser.keyOwner(read("{}"), "k")).isEmpty();
    assertThat(RemoteActorParser.host("ftp://x.example/a")).isNull();
    assertThat(RemoteActorParser.host("::not a uri")).isNull();
  }

  @Test
  void aKeyDocumentNamesItsOwnerAndArraysOfKeysPickTheRequestedOne() {
    JsonNode keyDoc =
        read(
            "{\"type\":\"Key\",\"id\":\"https://x.example/k\",\"owner\":\"https://x.example/u\",\"publicKeyPem\":\"p\"}");
    assertThat(RemoteActorParser.keyOwner(keyDoc, "https://x.example/k"))
        .contains("https://x.example/u");

    ObjectNode twoKeys = (ObjectNode) read(RemoteActorFixtures.MASTODON);
    var first = twoKeys.get("publicKey").deepCopy();
    ((ObjectNode) first).put("id", "https://mastodon.example/users/alice#old-key");
    twoKeys
        .putArray("publicKey")
        .add(first)
        .add(read(RemoteActorFixtures.MASTODON).get("publicKey"));
    var actor =
        RemoteActorParser.parse(
                twoKeys,
                URI.create("https://mastodon.example/users/alice"),
                "https://mastodon.example/users/alice#main-key")
            .orElseThrow();
    assertThat(actor.keyId()).isEqualTo("https://mastodon.example/users/alice#main-key");
    assertThat(
            RemoteActorParser.parse(
                twoKeys,
                URI.create("https://mastodon.example/users/alice"),
                "https://mastodon.example/users/alice#missing"))
        .isEmpty();
  }
}
