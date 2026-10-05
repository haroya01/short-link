package com.example.short_link.federation.domain;

public record RemoteActorDocument(
    String actorUri,
    String keyId,
    String publicKeyPem,
    String inbox,
    String sharedInbox,
    String username,
    String domain,
    String profileUrl,
    String displayName,
    String avatarUrl) {}
