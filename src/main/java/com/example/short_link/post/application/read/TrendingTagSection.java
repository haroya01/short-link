package com.example.short_link.post.application.read;

import java.util.List;

/**
 * One topic row of the 인기(trending) tab: a tag, how many published posts carry it, and the top
 * posts under it. GET /api/v1/public/feed/trending-by-tag returns a list of these.
 */
public record TrendingTagSection(String tag, long postCount, List<PublicFeedItem> posts) {}
