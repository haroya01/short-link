package com.example.short_link.post.application.read;

import java.util.List;

public record PublicFeedView(List<PublicFeedItem> items, int page, int size, boolean hasNext) {}
