package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ordered post ids the author pins to the top of their public profile (0-based = list order). */
public record SetPinnedPostsRequest(@NotNull @Size(max = 100) List<Long> postIds) {}
