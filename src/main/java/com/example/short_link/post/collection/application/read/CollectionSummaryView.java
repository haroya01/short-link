package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import java.time.Instant;
import java.util.List;

public record CollectionSummaryView(
    Long id,
    String title,
    String description,
    String visibility,
    String kind,
    boolean ordered,
    int count,
    Instant updatedAt,
    List<String> preview,
    String curatorUsername,
    String curatorAvatarUrl,
    Integer position,
    Long connectionId,
    String unlistedUrl) {

  public static CollectionSummaryView afterCreation(
      CollectionEntity collection, String unlistedUrl) {
    return afterWrite(collection, 0, unlistedUrl);
  }

  public static CollectionSummaryView afterWrite(
      CollectionEntity collection, long count, String unlistedUrl) {
    return from(collection, count, List.of(), Curator.UNKNOWN, null, null, unlistedUrl);
  }

  public static CollectionSummaryView inList(
      CollectionEntity collection,
      long count,
      List<String> preview,
      Curator curator,
      Long connectionId,
      String unlistedUrl) {
    return from(collection, count, preview, curator, null, connectionId, unlistedUrl);
  }

  public static CollectionSummaryView containingBlock(
      CollectionEntity collection, long count, Curator curator, Integer position) {
    return from(collection, count, List.of(), curator, position, null, null);
  }

  private static CollectionSummaryView from(
      CollectionEntity collection,
      long count,
      List<String> preview,
      Curator curator,
      Integer position,
      Long connectionId,
      String unlistedUrl) {
    return new CollectionSummaryView(
        collection.getId(),
        collection.getTitle(),
        collection.getDescription(),
        collection.getVisibility().name(),
        CollectionKind.of(collection.isOrdered()).name(),
        collection.isOrdered(),
        (int) count,
        collection.getUpdatedAt(),
        preview,
        curator.username(),
        curator.avatarUrl(),
        position,
        connectionId,
        unlistedUrl);
  }

  public record Curator(String username, String avatarUrl) {
    public static final Curator UNKNOWN = new Curator(null, null);
  }
}
