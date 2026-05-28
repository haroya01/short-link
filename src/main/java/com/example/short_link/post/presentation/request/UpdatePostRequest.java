package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;

/** PATCH 의미. null = 변경 안 함. excerpt / ogImageUrl 은 빈 문자열로 explicit clear. */
public record UpdatePostRequest(
    @Size(min = 1, max = 200) String title,
    @Size(min = 2, max = 200) String slug,
    @Size(max = 500) String excerpt,
    @Size(max = 512) String ogImageUrl,
    @Size(max = 256) String ogImageKey,
    @Size(max = 16) String languageTag) {}
