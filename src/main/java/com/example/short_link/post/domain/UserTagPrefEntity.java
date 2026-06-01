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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One user's preference toward a tag. (user_id, tag) is unique — FOLLOW and HIDE are exclusive. */
@Entity
@Table(
    name = "user_tag_pref",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_tag_pref_user_tag",
            columnNames = {"user_id", "tag"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTagPrefEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 40)
  private String tag;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private TagPrefKind kind;

  public UserTagPrefEntity(Long userId, String tag, TagPrefKind kind) {
    this.userId = userId;
    this.tag = tag;
    this.kind = kind;
  }

  public void changeKind(TagPrefKind kind) {
    this.kind = kind;
  }
}
