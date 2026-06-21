package com.example.short_link.post.collection.domain.repository.projection;

/**
 * 큐레이션 그래프 — 사람이 손으로 엮은 *공개* 컬렉션의 연결만으로 derivable(쿠키·추적 없이). 한 블록과 같은 길에 함께 놓인 블록("이것과 이어진 것"), 그리고
 * 같은 것을 엮은 다른 큐레이터("취향이 겹치는 사람") — 연결로 잇는 발견(broadcast 아님).
 */
public final class CurationGraphProjections {

  private CurationGraphProjections() {}

  /** 같은 공개 컬렉션에 함께 놓인 블록 — sharedCount = 함께 놓인 공개 컬렉션 수. */
  public interface CooccurrenceRow {
    String getBlockType();

    Long getRefId();

    Long getSharedCount();
  }

  /** 같은 블록을 자기 공개 컬렉션에 엮은 다른 큐레이터 — sharedItems = 겹치는 블록 수. */
  public interface CuratorOverlapRow {
    Long getCuratorId();

    Long getSharedItems();
  }
}
