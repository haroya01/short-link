package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Public series summary for the author's series index. postCount counts published members only.
 * tags are the distinct tags across the series' published members — the index's tag filter. The id
 * is the subscribe target (the series page's 구독 toggle), mirroring the discovery series card.
 */
public record PublicSeriesListItem(
    long id, String slug, String title, int postCount, List<String> tags) {}
