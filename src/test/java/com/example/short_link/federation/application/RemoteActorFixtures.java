package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.PemKeys;

final class RemoteActorFixtures {

  static final String PEM = new ActorKeys().generate().publicKey();

  static final String MASTODON =
      """
      {
        "@context": ["https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"],
        "id": "https://mastodon.example/users/alice",
        "type": "Person",
        "preferredUsername": "alice",
        "name": "Alice",
        "inbox": "https://mastodon.example/users/alice/inbox",
        "outbox": "https://mastodon.example/users/alice/outbox",
        "endpoints": {"sharedInbox": "https://mastodon.example/inbox"},
        "url": "https://mastodon.example/@alice",
        "icon": {"type": "Image", "mediaType": "image/png", "url": "https://files.mastodon.example/a.png"},
        "publicKey": {
          "id": "https://mastodon.example/users/alice#main-key",
          "owner": "https://mastodon.example/users/alice",
          "publicKeyPem": %s
        }
      }
      """
          .formatted(quoted(PEM));

  static final String GOTOSOCIAL_KEY_STUB =
      """
      {
        "@context": ["https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"],
        "id": "https://gts.example/users/bob",
        "type": "Person",
        "preferredUsername": "bob",
        "publicKey": {
          "id": "https://gts.example/users/bob/main-key",
          "owner": "https://gts.example/users/bob",
          "publicKeyPem": %s
        }
      }
      """
          .formatted(quoted(PEM));

  static final String GOTOSOCIAL_ACTOR =
      """
      {
        "@context": ["https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"],
        "id": "https://gts.example/users/bob",
        "type": "Person",
        "preferredUsername": "bob",
        "inbox": "https://gts.example/users/bob/inbox",
        "url": "https://gts.example/@bob",
        "icon": [{"type": "Image", "url": "https://gts.example/media/b.png"}],
        "publicKey": {
          "id": "https://gts.example/users/bob/main-key",
          "owner": "https://gts.example/users/bob",
          "publicKeyPem": %s
        }
      }
      """
          .formatted(quoted(PEM));

  private RemoteActorFixtures() {}

  static boolean pemIsValid() {
    return PemKeys.publicKey(PEM).isPresent();
  }

  private static String quoted(String value) {
    return "\"" + value.replace("\n", "\\n") + "\"";
  }
}
