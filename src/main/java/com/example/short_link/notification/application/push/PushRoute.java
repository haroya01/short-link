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

  public static PushRoute toPost(
      String actorUsername, String ownerUsername, String slug, Long commentId, Long highlightId) {
    return new PushRoute(
        actorUsername, ownerUsername, slug, null, null, commentId, highlightId, null);
  }

  public static PushRoute toSeries(String actorUsername, String ownerUsername, String slug) {
    return new PushRoute(actorUsername, ownerUsername, null, slug, null, null, null, null);
  }

  public static PushRoute toCollection(String actorUsername, Long collectionId) {
    return new PushRoute(actorUsername, null, null, null, collectionId, null, null, null);
  }

  public static PushRoute toNote(String actorUsername, String ownerUsername, Long noteId) {
    return new PushRoute(actorUsername, ownerUsername, null, null, null, null, null, noteId);
  }

  public static PushRoute toActor(String actorUsername) {
    return new PushRoute(actorUsername, null, null, null, null, null, null, null);
  }
}
