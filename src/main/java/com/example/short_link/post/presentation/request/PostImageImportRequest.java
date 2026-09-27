package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record PostImageImportRequest(@NotBlank String url) {}
