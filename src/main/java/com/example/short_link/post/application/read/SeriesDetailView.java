package com.example.short_link.post.application.read;

import java.util.List;

public record SeriesDetailView(
    SeriesView series, List<PostView> posts, List<SeriesItemView> items) {}
