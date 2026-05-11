package com.example.short_link.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileBlockEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "block_type", nullable = false, length = 16)
  private ProfileBlockType type;

  /**
   * Type-specific payload. TEXT: section header text. IMAGE/EMBED: URL. EMAIL_FORM / CONTACT_CARD /
   * GALLERY / PRODUCT_CARD: JSON config. DIVIDER: null. Stored as TEXT to fit PRODUCT_CARD's
   * multi-item JSON (up to 8 items with image URLs + descriptions).
   */
  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "profile_order", nullable = false)
  private Integer profileOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ProfileBlockEntity(Long userId, ProfileBlockType type, String content, int profileOrder) {
    this.userId = userId;
    this.type = type;
    this.content = content;
    this.profileOrder = profileOrder;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public void updateContent(String content) {
    this.content = content;
  }

  public void setProfileOrder(int profileOrder) {
    this.profileOrder = profileOrder;
  }
}
