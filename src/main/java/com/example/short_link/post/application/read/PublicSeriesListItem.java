package com.example.short_link.post.application.read;

import java.util.List;

/**
 * {@code postCount} and distinct {@code tags} include published members only; {@code id} is the
 * subscribe target.
 */
public record PublicSeriesListItem(
    long id, String slug, String title, int postCount, List<String> tags) {}
