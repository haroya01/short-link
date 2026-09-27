package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SetPinnedPostsRequest(@NotNull @Size(max = 100) List<Long> postIds) {}
