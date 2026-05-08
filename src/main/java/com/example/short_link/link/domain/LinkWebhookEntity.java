package com.example.short_link.link.domain;

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
@Table(name = "link_webhook")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkWebhookEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false, length = 64)
  private String secret;

  @Column(length = 100)
  private String name;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_called_at")
  private Instant lastCalledAt;

  @Column(name = "last_status_code")
  private Integer lastStatusCode;

  @Column(name = "last_error", length = 500)
  private String lastError;

  public LinkWebhookEntity(Long linkId, String url, String secret, String name) {
    this.linkId = linkId;
    this.url = url;
    this.secret = secret;
    this.name = name;
    this.enabled = true;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public void disable() {
    this.enabled = false;
  }

  public void enable() {
    this.enabled = true;
  }

  public void recordSuccess(int status) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = null;
  }

  public void recordFailure(Integer status, String error) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
  }
}
