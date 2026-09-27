package com.example.short_link.post.collection.domain.repository.projection;

public final class CurationGraphProjections {

  private CurationGraphProjections() {}

  public interface CooccurrenceRow {
    String getBlockType();

    Long getRefId();

    Long getSharedCount();
  }

  public interface CuratorOverlapRow {
    Long getCuratorId();

    Long getSharedItems();
  }
}
