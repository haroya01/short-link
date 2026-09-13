package com.example.short_link.post.application.read;

import java.util.List;

/** {@code page} is zero-based; ordering follows the requested performance sort. */
public record PostPerformanceResult(List<TopPostView> items, int page, boolean hasNext) {}
