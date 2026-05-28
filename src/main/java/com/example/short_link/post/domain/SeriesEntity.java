package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
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
 * A named, ordered grouping of an author's posts (velog-style series). Membership + ordering live
 * on {@link PostEntity#getSeriesId()} / {@code seriesOrder} so a post can be reordered without
 * touching this aggregate. Unlike post slugs, a series slug is not frozen — series are
 * organizational and renaming them is low-stakes.
 */
@Entity
@Table(
    name = "series",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_series_user_slug",
            columnNames = {"user_id", "slug"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeriesEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 200)
  private String slug;

  @Column(nullable = false, length = 200)
  private String title;

  public SeriesEntity(Long userId, String slug, String title) {
    this.userId = userId;
    this.slug = slug;
    this.title = title;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  public void updateSlug(String slug) {
    this.slug = slug;
  }
}
