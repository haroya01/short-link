package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.collection.domain.CollectionEntity;
import java.time.Instant;
import java.util.List;

/**
 * 컬렉션의 기본 정보와 조회 맥락. 목록은 preview와 기존 connectionId를, 역조회는 블록의 1-based position을 채운다. 생성·수정 응답은 저장한
 * 기본 정보와 count만 돌려준다.
 */
public record CollectionSummaryView(
    Long id,
    String title,
    String description,
    String visibility,
    String kind,
    int count,
    Instant updatedAt,
    List<String> preview,
    String curatorUsername,
    String curatorAvatarUrl,
    Integer position,
    Long connectionId) {

  public static CollectionSummaryView afterCreation(CollectionEntity collection) {
    return afterWrite(collection, 0);
  }

  public static CollectionSummaryView afterWrite(CollectionEntity collection, long count) {
    return from(collection, count, List.of(), Curator.UNKNOWN, null, null);
  }

  public static CollectionSummaryView inList(
      CollectionEntity collection,
      long count,
      List<String> preview,
      Curator curator,
      Long connectionId) {
    return from(collection, count, preview, curator, null, connectionId);
  }

  public static CollectionSummaryView containingBlock(
      CollectionEntity collection, long count, Curator curator, Integer position) {
    return from(collection, count, List.of(), curator, position, null);
  }

  private static CollectionSummaryView from(
      CollectionEntity collection,
      long count,
      List<String> preview,
      Curator curator,
      Integer position,
      Long connectionId) {
    return new CollectionSummaryView(
        collection.getId(),
        collection.getTitle(),
        collection.getDescription(),
        collection.getVisibility().name(),
        collection.getKind().name(),
        (int) count,
        collection.getUpdatedAt(),
        preview,
        curator.username(),
        curator.avatarUrl(),
        position,
        connectionId);
  }

  /** 조회한 큐레이터의 표시 정보. 누락된 사용자는 기존 응답처럼 두 필드를 null로 둔다. */
  public record Curator(String username, String avatarUrl) {
    public static final Curator UNKNOWN = new Curator(null, null);
  }
}
