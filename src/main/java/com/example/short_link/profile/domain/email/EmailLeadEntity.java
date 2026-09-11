package com.example.short_link.profile.domain.email;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_lead")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailLeadEntity {

  private static final int EMAIL_MAX_LENGTH = 254;
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

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
    String normalizedEmail = normalizeEmail(email);
    this.userId = userId;
    this.blockId = blockId;
    this.email = normalizedEmail;
    this.ipHash = ipHash;
  }

  private static String normalizeEmail(String raw) {
    String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_EMAIL, "email required");
    }
    if (normalized.length() > EMAIL_MAX_LENGTH) {
      throw new ProfileException(ProfileErrorCode.INVALID_EMAIL, "email too long");
    }
    if (!EMAIL.matcher(normalized).matches()) {
      throw new ProfileException(ProfileErrorCode.INVALID_EMAIL, "email malformed");
    }
    return normalized;
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
