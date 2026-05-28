package com.example.short_link.post.application.read;

import java.util.List;

/** Owner-facing series detail: the series plus its ordered member posts (any status). */
public record SeriesDetailView(SeriesView series, List<PostView> posts) {}
