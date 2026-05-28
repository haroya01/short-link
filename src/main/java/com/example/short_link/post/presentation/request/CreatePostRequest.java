package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
    @NotBlank @Size(min = 2, max = 200) String slug,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 16) String languageTag) {}
