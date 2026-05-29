package com.example.short_link.user.domain;

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

/** One follow edge: {@code followerId} follows {@code followingId}. The pair is unique. */
@Entity
@Table(
    name = "user_follow",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_follow",
            columnNames = {"follower_id", "following_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "follower_id", nullable = false)
  private Long followerId;

  @Column(name = "following_id", nullable = false)
  private Long followingId;

  public FollowEntity(Long followerId, Long followingId) {
    this.followerId = followerId;
    this.followingId = followingId;
  }
}
