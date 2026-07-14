package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Admin moderation edit — PATCH semantics (null = leave unchanged), same field limits as the
 * owner's {@link UpdatePostRequest}. Title/tags only: the fields moderation actually rewrites;
 * body, slug, cover and excerpt stay the author's.
 */
public record AdminUpdatePostRequest(
    @Size(max = 200) String title, @Size(max = 100) List<@Size(max = 80) String> tags) {}
