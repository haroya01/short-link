package com.example.short_link.post.application.read;

import java.util.List;

public record TrendingTagSection(String tag, long postCount, List<PublicFeedItem> posts) {}
