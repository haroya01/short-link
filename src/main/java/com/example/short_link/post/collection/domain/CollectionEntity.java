package com.example.short_link.post.collection.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
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
 * 컬렉션(Are.na 채널) — 글·하이라이트·노트를 주제로 잇는 큐레이션 단위. §0 "읽기의 연결 그래프"의 그릇. 담긴 블록은 {@link
 * CollectionConnectionEntity} 로 다대다 연결되며(멀티멤버십), 컬렉션 자체는 좋아요·팔로워 수 같은 허영 지표를 들지 않는다(바깥은 조용히).
 */
@Entity
@Table(name = "collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionEntity extends BaseTimeEntity {

  public static final int MAX_TITLE = 120;
  public static final int MAX_DESCRIPTION = 280;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = MAX_TITLE)
  private String title;

  @Column(length = MAX_DESCRIPTION)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CollectionVisibility visibility;

  public CollectionEntity(
      Long ownerId, String title, String description, CollectionVisibility visibility) {
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
    this.visibility = visibility;
  }

  public boolean isOwnedBy(Long viewerId) {
    return ownerId.equals(viewerId);
  }

  /** 이 뷰어가 볼 수 있는가 — 주인은 항상, 그 외엔 PRIVATE 만 막힌다. */
  public boolean isVisibleTo(Long viewerId) {
    return isOwnedBy(viewerId) || visibility.isVisibleToOthers();
  }

  public void edit(String title, String description, CollectionVisibility visibility) {
    this.title = title;
    this.description = description;
    this.visibility = visibility;
  }
}
