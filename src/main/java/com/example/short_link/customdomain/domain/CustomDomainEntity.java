package com.example.short_link.customdomain.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code _kurl-verify.<domain>}의 TXT 토큰으로 소유권을 검증한다. 검증 전 도메인은 리다이렉트에 사용하지 않는다. */
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
    String normalized = domain == null ? null : domain.trim().toLowerCase(Locale.ROOT);
    validateDomain(normalized);
    this.userId = userId;
    this.domain = normalized;
    this.verificationToken = verificationToken;
  }

  public void markVerified() {
    Instant checkedAt = Instant.now();
    if (!this.verified) {
      this.verified = true;
      this.verifiedAt = checkedAt;
    }
    this.lastCheckedAt = checkedAt;
  }

  public void markCheckFailed() {
    this.lastCheckedAt = Instant.now();
  }

  public static void validateDomain(String domain) {
    if (domain == null || domain.isBlank() || domain.length() > 253) {
      throw new IllegalArgumentException("invalid domain");
    }
    if (!domain.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")) {
      throw new IllegalArgumentException("invalid domain format");
    }
    if (domain.equals("kurl.me") || domain.endsWith(".kurl.me")) {
      throw new IllegalArgumentException("cannot register kurl.me itself");
    }
  }
}
