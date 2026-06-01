package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One subscription edge: {@code userId} subscribes to series {@code seriesId}. The pair is unique.
 */
@Entity
@Table(
    name = "series_subscription",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_series_subscription_user_series",
            columnNames = {"user_id", "series_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeriesSubscriptionEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "series_id", nullable = false)
  private Long seriesId;

  public SeriesSubscriptionEntity(Long userId, Long seriesId) {
    this.userId = userId;
    this.seriesId = seriesId;
  }
}
