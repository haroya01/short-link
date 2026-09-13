package com.example.short_link.post.application.read;

import java.util.List;

/** Member posts are in series order and include every publication status. */
public record SeriesDetailView(SeriesView series, List<PostView> posts) {}
