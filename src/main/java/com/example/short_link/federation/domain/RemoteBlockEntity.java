package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
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
@Table(name = "federation_remote_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemoteBlockEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "remote_actor_id", nullable = false)
  private Long remoteActorId;

  @Column(name = "activity_id", length = 512)
  private String activityId;

  public RemoteBlockEntity(Long userId, Long remoteActorId, String activityId) {
    this.userId = userId;
    this.remoteActorId = remoteActorId;
    this.activityId = activityId;
  }
}
