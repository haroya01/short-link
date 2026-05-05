package com.example.short_link.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
  private String originalUrl;

  @Column(name = "short_code", nullable = false, length = 16, unique = true)
  private String shortCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public LinkEntity(String originalUrl, String shortCode) {
    this.originalUrl = originalUrl;
    this.shortCode = shortCode;
    this.createdAt = LocalDateTime.now();
  }
}
