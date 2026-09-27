package com.example.short_link.post.collection.domain;

import java.time.Instant;

public record DiscoverConnectionRow(
    Long connectionId,
    ConnectionBlockType blockType,
    Long refId,
    String why,
    Instant connectedAt,
    Long collectionId,
    String collectionTitle,
    CollectionKind kind,
    Long ownerId) {}
