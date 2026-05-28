package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;

/** PATCH 의미. null = 변경 안 함. */
public record UpdateSeriesRequest(
    @Size(min = 1, max = 200) String title, @Size(min = 2, max = 200) String slug) {}
