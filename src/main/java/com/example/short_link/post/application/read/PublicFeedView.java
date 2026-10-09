package com.example.short_link.post.application.read;

import java.util.List;

// seriesNotes are the notes of subscribed series that fall on this page of the subscription feed;
// clients place them among the posts by time. Every other feed leaves them empty.
public record PublicFeedView(
    List<PublicFeedItem> items,
    int page,
    int size,
    boolean hasNext,
    List<FeedSeriesNote> seriesNotes) {

  public PublicFeedView(List<PublicFeedItem> items, int page, int size, boolean hasNext) {
    this(items, page, size, hasNext, List.of());
  }
}
