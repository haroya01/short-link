package com.example.short_link.post.application.read;

/**
 * Public series summary for the author's series index. postCount counts published members only. The
 * id is the subscribe target (the series page's 구독 toggle), mirroring the discovery series card.
 */
public record PublicSeriesListItem(long id, String slug, String title, int postCount) {}
