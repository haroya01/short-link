package com.example.short_link.post.domain;

/**
 * Clicks a single kurl link embedded in a post drove. Powers the per-post "글 안 링크별 클릭" breakdown —
 * the aggregate is already on the card; this attributes it to each link/destination.
 *
 * @param shortCode the link's short code (e.g. {@code abc123})
 * @param destinationUrl where it points (the author's original URL)
 * @param clicks total clicks attributed to this link from this post
 */
public record PostLinkClick(String shortCode, String destinationUrl, long clicks) {}
