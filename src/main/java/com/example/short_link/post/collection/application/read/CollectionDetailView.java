package com.example.short_link.post.collection.application.read;

import java.util.List;

public record CollectionDetailView(
    Long id,
    String title,
    String description,
    String visibility,
    String kind,
    String curatorUsername,
    List<ConnectionView> connections) {}
