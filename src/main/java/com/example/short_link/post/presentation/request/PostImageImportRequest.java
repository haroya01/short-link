package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;

/** Re-host an external image URL (e.g. pasted from Notion) into our bucket. */
public record PostImageImportRequest(@NotBlank String url) {}
