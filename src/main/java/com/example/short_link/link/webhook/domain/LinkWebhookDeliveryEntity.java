package com.example.short_link.link.webhook.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
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
@Table(name = "link_webhook_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkWebhookDeliveryEntity extends BaseTimeEntity {

  private static final int ERROR_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hook_id", nullable = false)
  private Long hookId;

  @Column(name = "click_id", nullable = false)
  private Long clickId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WebhookDeliveryStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_status")
  private Integer lastStatus;

  @Column(name = "last_error", length = ERROR_LENGTH)
  private String lastError;

  public LinkWebhookDeliveryEntity(Long hookId, Long clickId, String payload, Instant now) {
    this.hookId = hookId;
    this.clickId = clickId;
    this.payload = payload;
    this.status = WebhookDeliveryStatus.PENDING;
    this.attempts = 0;
    this.nextAttemptAt = now;
  }

  public void lease(Instant until) {
    this.attempts++;
    this.nextAttemptAt = until;
  }

  public void delivered(Integer status) {
    this.status = WebhookDeliveryStatus.DONE;
    this.lastStatus = status;
    this.lastError = null;
  }

  public void retryAt(Instant next, Integer status, String error) {
    this.status = WebhookDeliveryStatus.PENDING;
    this.nextAttemptAt = next;
    this.lastStatus = status;
    this.lastError = truncate(error);
  }

  public void giveUp(Integer status, String error) {
    this.status = WebhookDeliveryStatus.GAVE_UP;
    this.lastStatus = status;
    this.lastError = truncate(error);
  }

  private static String truncate(String error) {
    return error == null || error.length() <= ERROR_LENGTH
        ? error
        : error.substring(0, ERROR_LENGTH);
  }
}
