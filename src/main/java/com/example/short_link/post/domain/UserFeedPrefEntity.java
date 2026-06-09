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

/** One user's feed preference — which blog-home tab opens by default. One row per user. */
@Entity
@Table(
    name = "user_feed_pref",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_feed_pref_user",
            columnNames = {"user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFeedPrefEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "default_tab", nullable = false, length = 16)
  private String defaultTab;

  public UserFeedPrefEntity(Long userId, String defaultTab) {
    this.userId = userId;
    this.defaultTab = defaultTab;
  }

  public void changeDefaultTab(String defaultTab) {
    this.defaultTab = defaultTab;
  }
}
