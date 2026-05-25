package com.example.short_link.admin.domain;

import com.example.short_link.admin.domain.repository.*;
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
@Table(name = "blocked_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockedDomainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String domain;

  @Column(length = 500)
  private String reason;

  @Column(name = "blocked_by_user_id")
  private Long blockedByUserId;

  @Column(name = "blocked_at", nullable = false)
  private Instant blockedAt;

  public BlockedDomainEntity(String domain, String reason, Long blockedByUserId) {
    this.domain = domain;
    this.reason = reason;
    this.blockedByUserId = blockedByUserId;
    this.blockedAt = Instant.now();
  }
}
