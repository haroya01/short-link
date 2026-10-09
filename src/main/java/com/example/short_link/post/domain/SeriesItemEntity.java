package com.example.short_link.post.domain;

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

// One post or note in a series, at its place in the series' single order.
@Entity
@Table(name = "series_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeriesItemEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "series_id", nullable = false)
  private Long seriesId;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false, length = 8)
  private SeriesItemType type;

  @Column(name = "ref_id", nullable = false)
  private Long refId;

  @Column(name = "position", nullable = false)
  private int position;

  public SeriesItemEntity(Long seriesId, SeriesItemType type, Long refId, int position) {
    this.seriesId = seriesId;
    this.type = type;
    this.refId = refId;
    this.position = position;
  }
}
