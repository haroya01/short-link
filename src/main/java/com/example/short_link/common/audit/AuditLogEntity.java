package com.example.short_link.common.audit;

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
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(name = "target_type", nullable = false, length = 30)
  private String targetType;

  @Column(name = "target_id", length = 64)
  private String targetId;

  @Column(columnDefinition = "json")
  private String metadata;

  @Column(name = "request_id", length = 40)
  private String requestId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  private AuditLogEntity(
      Long actorUserId,
      String action,
      String targetType,
      String targetId,
      String metadata,
      String requestId,
      Instant occurredAt) {
    this.actorUserId = actorUserId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.metadata = metadata;
    this.requestId = requestId;
    this.occurredAt = occurredAt;
  }

  /**
   * Single intended creation path — caller spells out every column so we don't get half-built rows.
   */
  public static AuditLogEntity record(
      Long actorUserId,
      String action,
      String targetType,
      String targetId,
      String metadataJson,
      String requestId,
      Instant occurredAt) {
    return new AuditLogEntity(
        actorUserId, action, targetType, targetId, metadataJson, requestId, occurredAt);
  }
}
