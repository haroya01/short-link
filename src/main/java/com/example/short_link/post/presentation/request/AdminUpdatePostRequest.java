package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/** PATCH semantics: null fields remain unchanged. */
public record AdminUpdatePostRequest(
    @Size(max = 200) String title, @Size(max = 100) List<@Size(max = 80) String> tags) {}
