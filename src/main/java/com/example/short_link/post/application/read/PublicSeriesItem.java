package com.example.short_link.post.application.read;

// One of post or note is set, as type says.
public record PublicSeriesItem(String type, PublicPostListItem post, SeriesNoteView note) {}
