package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;

public record UpdateSeriesRequest(
    @Size(min = 1, max = 200) String title, @Size(min = 2, max = 200) String slug) {}
