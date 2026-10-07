package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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

  // Null while a locked member has not approved the follow: no follower-only note goes there.
  @Column(name = "accepted_at")
  private Instant acceptedAt;

  public FederationFollowerEntity(Long userId, Long remoteActorId, String followActivityId) {
    this.userId = userId;
    this.remoteActorId = remoteActorId;
    this.followActivityId = followActivityId;
  }

  public boolean isPending() {
    return acceptedAt == null;
  }

  public void accept(Instant at) {
    this.acceptedAt = at;
  }

  public void refollow(String followActivityId) {
    this.followActivityId = followActivityId;
  }
}
