package com.example.short_link.post.collection.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연결 = (컬렉션 × 블록) 한 줄. {@code refId} 는 {@code blockType} 에 따라 post / post_highlight / note 의 PK 를
 * 가리키는 다형 참조다(세 테이블 동시 FK 불가라 애플리케이션이 대상 존재를 검증). {@code why} 는 "왜 이었나" 한 줄(선택) — 큐레이션의 목소리. 같은 블록을
 * 같은 컬렉션에 두 번 못 잇는다(유니크).
 */
@Entity
@Table(name = "collection_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionConnectionEntity extends BaseCreatedEntity {

  public static final int MAX_WHY = 280;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "collection_id", nullable = false)
  private Long collectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "block_type", nullable = false, length = 16)
  private ConnectionBlockType blockType;

  @Column(name = "ref_id", nullable = false)
  private Long refId;

  @Column(length = MAX_WHY)
  private String why;

  @Column(nullable = false)
  private Integer position;

  public CollectionConnectionEntity(
      Long collectionId, ConnectionBlockType blockType, Long refId, String why, Integer position) {
    this.collectionId = collectionId;
    this.blockType = blockType;
    this.refId = refId;
    this.why = why;
    this.position = position;
  }

  /** 재배치(reorder) — PATH 에서 순서가 곧 논증 흐름이라 position 을 새 값으로 옮긴다. */
  public void reposition(int position) {
    this.position = position;
  }
}
