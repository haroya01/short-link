package com.example.short_link.post.application.read;

import java.util.List;

public record PostPerformanceResult(List<TopPostView> items, int page, boolean hasNext) {}
