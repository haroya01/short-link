package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_relay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationRelayEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_uri", nullable = false, length = 512)
  private String actorUri;

  @Column(nullable = false, length = 512)
  private String inbox;

  @Column(length = 255)
  private String label;

  @Column(name = "follow_activity_id", nullable = false, length = 600)
  private String followActivityId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RelayState state;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  public FederationRelayEntity(
      String actorUri, String inbox, String label, String followActivityId) {
    this.actorUri = actorUri;
    this.inbox = inbox;
    this.label = label;
    this.followActivityId = followActivityId;
    this.state = RelayState.PENDING;
  }

  public void answered(boolean accepted, Instant at) {
    this.state = accepted ? RelayState.ACCEPTED : RelayState.REJECTED;
    this.acceptedAt = accepted ? at : null;
  }
}
