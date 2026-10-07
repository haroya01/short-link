package com.example.short_link.portability.domain;

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
@Table(name = "account_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountImportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ImportKind kind;

  @Column(name = "total_items", nullable = false)
  private int totalItems;

  @Column(name = "processed_items", nullable = false)
  private int processedItems;

  @Column(name = "imported_items", nullable = false)
  private int importedItems;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  public AccountImportEntity(Long userId, ImportKind kind, int totalItems, Instant createdAt) {
    this.userId = userId;
    this.kind = kind;
    this.totalItems = totalItems;
    this.createdAt = createdAt;
  }

  public boolean isFinished() {
    return finishedAt != null;
  }
}
