package com.example.short_link.post.collection.application.read;

import java.util.List;

public record PostCollectionsView(Long postId, List<CollectionSummaryView> collections) {}
