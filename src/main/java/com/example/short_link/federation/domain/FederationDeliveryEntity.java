package com.example.short_link.federation.domain;

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

// A null signer means the instance actor signs. dedupe_key = sha256(activity id + inbox) makes a
// repeated enqueue of the same activity to the same inbox a no-op.
@Entity
@Table(name = "federation_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FederationDeliveryEntity extends BaseTimeEntity {

  private static final int ERROR_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "dedupe_key", nullable = false, length = 64)
  private String dedupeKey;

  @Column(nullable = false, length = 512)
  private String inbox;

  @Column(name = "inbox_host", nullable = false, length = 255)
  private String inboxHost;

  @Column(name = "signer_user_id")
  private Long signerUserId;

  @Column(name = "activity_id", nullable = false, length = 512)
  private String activityId;

  @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DeliveryStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_status")
  private Integer lastStatus;

  @Column(name = "last_error", length = ERROR_LENGTH)
  private String lastError;

  public FederationDeliveryEntity(
      String dedupeKey,
      String inbox,
      String inboxHost,
      Long signerUserId,
      String activityId,
      String body,
      Instant now) {
    this.dedupeKey = dedupeKey;
    this.inbox = inbox;
    this.inboxHost = inboxHost;
    this.signerUserId = signerUserId;
    this.activityId = activityId;
    this.body = body;
    this.status = DeliveryStatus.PENDING;
    this.attempts = 0;
    this.nextAttemptAt = now;
  }

  public void lease(Instant until) {
    this.attempts++;
    this.nextAttemptAt = until;
  }

  public void delivered(int status) {
    this.status = DeliveryStatus.DONE;
    this.lastStatus = status;
    this.lastError = null;
  }

  public void retryAt(Instant next, Integer status, String error) {
    this.status = DeliveryStatus.PENDING;
    this.nextAttemptAt = next;
    this.lastStatus = status;
    this.lastError = truncate(error);
  }

  public void giveUp(Integer status, String error) {
    this.status = DeliveryStatus.GAVE_UP;
    this.lastStatus = status;
    this.lastError = truncate(error);
  }

  private static String truncate(String error) {
    return error == null || error.length() <= ERROR_LENGTH
        ? error
        : error.substring(0, ERROR_LENGTH);
  }
}
