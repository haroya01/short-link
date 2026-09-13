package com.example.short_link.post.domain;

/**
 * {@code clicks} counts clicks attributed to this link from this post; {@code destinationUrl} is
 * the author's original destination.
 */
public record PostLinkClick(String shortCode, String destinationUrl, long clicks) {}
