package com.example.short_link.post.collection.presentation.request;

import com.example.short_link.post.collection.domain.CollectionVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditCollectionRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 280) String description,
    CollectionVisibility visibility) {}
