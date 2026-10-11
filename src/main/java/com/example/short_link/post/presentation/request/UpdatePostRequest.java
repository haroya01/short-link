package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePostRequest(
    // Blank titles are allowed for drafts; publishing requires a title.
    @Size(max = 200) String title,
    @Size(min = 1, max = 200) String slug,
    @Size(max = 500) String excerpt,
    @Size(max = 512) String ogImageUrl,
    @Size(max = 256) String ogImageKey,
    Boolean coverChosen,
    @Size(max = 16) String languageTag,
    @Size(max = 100) List<@Size(max = 80) String> tags,
    @PositiveOrZero Long baseVersion,
    Boolean overwrite) {}
