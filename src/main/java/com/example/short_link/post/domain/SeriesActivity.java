package com.example.short_link.post.domain;

import java.time.Instant;

public record SeriesActivity(Long seriesId, long postCount, Instant lastPublishedAt) {}
