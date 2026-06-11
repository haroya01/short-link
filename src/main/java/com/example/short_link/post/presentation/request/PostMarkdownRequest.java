package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostMarkdownRequest(@NotNull @Size(max = 200_000) String markdown) {}
