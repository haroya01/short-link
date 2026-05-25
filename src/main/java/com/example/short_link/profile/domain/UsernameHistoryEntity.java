package com.example.short_link.profile.domain;

import com.example.short_link.profile.domain.repository.*;
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
 * Records a username freed up by a rename. While the row is unexpired:
 *
 * <ul>
 *   <li>Visitors hitting the old handle get redirected to the owner's current handle.
 *   <li>Other users cannot claim the old handle (squat protection).
 * </ul>
 *
 * <p>Expiration is fixed at the time of rename ({@code changedAt + 30 days}). Once expired the row
 * is ignored — the handle is fully released.
 */
@Entity
@Table(name = "username_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsernameHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "old_username", nullable = false, length = 32)
  private String oldUsername;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public UsernameHistoryEntity(Long userId, String oldUsername, Instant expiresAt) {
    this.userId = userId;
    this.oldUsername = oldUsername.toLowerCase();
    this.changedAt = Instant.now();
    this.expiresAt = expiresAt;
  }
}
