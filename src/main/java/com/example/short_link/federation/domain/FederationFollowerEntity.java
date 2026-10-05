package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_follower")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationFollowerEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "remote_actor_id", nullable = false)
  private Long remoteActorId;

  @Column(name = "follow_activity_id", nullable = false, length = 512)
  private String followActivityId;

  public FederationFollowerEntity(Long userId, Long remoteActorId, String followActivityId) {
    this.userId = userId;
    this.remoteActorId = remoteActorId;
    this.followActivityId = followActivityId;
  }

  public void refollow(String followActivityId) {
    this.followActivityId = followActivityId;
  }
}
