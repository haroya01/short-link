package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
    @NotBlank @Size(min = 2, max = 200) String slug,
    // Title may be blank while drafting; it's required only at publish (PostEntity.publish()).
    @Size(max = 200) String title,
    @Size(max = 16) String languageTag) {}
