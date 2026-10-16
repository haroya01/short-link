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

@Entity
@Table(name = "federation_directory_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationDirectorySourceEntity extends BaseTimeEntity {

  @Id
  @Column(length = 255)
  private String host;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int cap;

  @Column(nullable = false)
  private int failures;

  @Column(name = "retry_after")
  private Instant retryAfter;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "last_outcome", length = 255)
  private String lastOutcome;

  public FederationDirectorySourceEntity(String host, boolean enabled, int cap) {
    this.host = host;
    change(enabled, cap);
  }

  public void change(boolean enabled, int cap) {
    this.enabled = enabled;
    this.cap = cap;
    this.failures = 0;
    this.retryAfter = null;
  }

  public void synced(String outcome, Instant at) {
    this.failures = 0;
    this.retryAfter = null;
    this.lastSyncedAt = at;
    this.lastOutcome = outcome;
  }

  public void failed(String outcome, Instant retryAfter) {
    this.failures++;
    this.retryAfter = retryAfter;
    this.lastOutcome = outcome;
  }
}
