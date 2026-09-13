package com.example.short_link.post.collection.domain.repository.projection;

public final class CurationGraphProjections {

  private CurationGraphProjections() {}

  /** {@code sharedCount}는 두 블록을 함께 담은 공개 컬렉션 수다. */
  public interface CooccurrenceRow {
    String getBlockType();

    Long getRefId();

    Long getSharedCount();
  }

  /** {@code sharedItems}는 공개 컬렉션에서 겹치는 블록 수다. */
  public interface CuratorOverlapRow {
    Long getCuratorId();

    Long getSharedItems();
  }
}
