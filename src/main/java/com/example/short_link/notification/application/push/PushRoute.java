package com.example.short_link.notification.application.push;

public record PushRoute(
    String actorUsername,
    String ownerUsername,
    String postSlug,
    String seriesSlug,
    Long collectionId,
    Long commentId,
    Long highlightId,
    Long noteId) {

  public PushRoute(
      String actorUsername,
      String ownerUsername,
      String postSlug,
      String seriesSlug,
      Long collectionId,
      Long commentId,
      Long highlightId) {
    this(
        actorUsername,
        ownerUsername,
        postSlug,
        seriesSlug,
        collectionId,
        commentId,
        highlightId,
        null);
  }
}
