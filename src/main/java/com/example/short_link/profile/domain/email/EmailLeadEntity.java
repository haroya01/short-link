package com.example.short_link.profile.domain.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_lead")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailLeadEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Profile owner — the user that gets to read / export this lead. */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** Which EMAIL_FORM block on the owner's profile produced this submission. */
  @Column(name = "block_id", nullable = false)
  private Long blockId;

  @Column(nullable = false, length = 254)
  private String email;

  /** sha256(ip + per-instance salt). Lets us dedupe burst submissions without storing raw IPs. */
  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  @Column(name = "opted_out", nullable = false)
  private boolean optedOut;

  public EmailLeadEntity(Long userId, Long blockId, String email, String ipHash) {
    this.userId = userId;
    this.blockId = blockId;
    this.email = email;
    this.ipHash = ipHash;
  }

  @PrePersist
  void onCreate() {
    this.submittedAt = Instant.now();
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public void setOptedOut(boolean optedOut) {
    this.optedOut = optedOut;
  }
}
