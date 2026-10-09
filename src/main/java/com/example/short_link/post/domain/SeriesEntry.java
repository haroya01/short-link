package com.example.short_link.post.domain;

import java.time.Instant;

// A post carries its slug, title and cover; a note its id and excerpt as the title. at is when it
// went out: a post's publication or a note's writing.
public record SeriesEntry(
    SeriesItemType type, Long refId, String slug, String title, String ogImageUrl, Instant at) {}
