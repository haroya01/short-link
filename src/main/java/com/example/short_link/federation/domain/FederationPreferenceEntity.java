package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// No row means the defaults: federation on (10-06 decision) and the first-note notice not yet seen.
@Entity
@Table(name = "federation_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationPreferenceEntity extends BaseTimeEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "notice_seen_at")
  private Instant noticeSeenAt;

  public FederationPreferenceEntity(Long userId) {
    this.userId = userId;
    this.enabled = true;
  }

  public void turnOn() {
    this.enabled = true;
  }

  public void turnOff() {
    this.enabled = false;
  }

  public void markNoticeSeen(Instant at) {
    if (noticeSeenAt == null) {
      this.noticeSeenAt = at;
    }
  }
}
