package com.example.short_link.post.application.read;

import java.util.List;

public record PublicSeriesListView(PublicAuthorView author, List<PublicSeriesListItem> series) {}
