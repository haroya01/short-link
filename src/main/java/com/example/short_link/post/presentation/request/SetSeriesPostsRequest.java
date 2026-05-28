package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ordered post ids that make up the series (0-based order = list order). */
public record SetSeriesPostsRequest(@NotNull @Size(max = 500) List<Long> postIds) {}
