package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;
import java.time.Instant;

public record DiscoverConnectionView(
    Long id,
    PublicAuthorView curator,
    Long collectionId,
    String collectionTitle,
    String collectionKind,
    String why,
    Instant connectedAt,
    String blockType,
    String title,
    String excerpt,
    String slug,
    String username,
    String quote,
    String body) {}
