package com.example.short_link.federation.domain;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.List;

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
    String avatarUrl,
    boolean discoverable,
    boolean indexable,
    List<CustomEmoji> emojis) {}
