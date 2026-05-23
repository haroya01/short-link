package com.example.short_link.link.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
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

/**
 * A domain the user wants to serve their short links from (e.g. {@code go.brand.com}). The
 * verification flow: we hand out a token, the user creates a TXT record at {@code
 * _kurl-verify.go.brand.com}, and a server-side check flips {@link #verified} to true. Only
 * verified rows are matched by the redirect router — unverified entries are pending claims with no
 * traffic effect.
 */
@Entity
@Table(name = "custom_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomDomainEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 255)
  private String domain;

  @Column(name = "verification_token", nullable = false, length = 64)
  private String verificationToken;

  @Column(nullable = false)
  private boolean verified = false;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  public CustomDomainEntity(Long userId, String domain, String verificationToken) {
    this.userId = userId;
    this.domain = domain.toLowerCase();
    this.verificationToken = verificationToken;
  }

  public void markVerified() {
    this.verified = true;
    this.verifiedAt = Instant.now();
    this.lastCheckedAt = this.verifiedAt;
  }

  public void markCheckFailed() {
    this.lastCheckedAt = Instant.now();
  }
}
