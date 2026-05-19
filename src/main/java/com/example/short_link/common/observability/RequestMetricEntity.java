package com.example.short_link.common.observability;

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
@Table(name = "request_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestMetricEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 255)
  private String route;

  @Column(nullable = false, length = 8)
  private String method;

  @Column(nullable = false)
  private int status;

  @Column(nullable = false, length = 32)
  private String outcome;

  @Column(name = "latency_ms", nullable = false)
  private long latencyMs;

  @Column(name = "short_code", length = 64)
  private String shortCode;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  public RequestMetricEntity(RequestMetric metric) {
    this.occurredAt = metric.occurredAt();
    this.route = metric.route();
    this.method = metric.method();
    this.status = metric.status();
    this.outcome = metric.outcome();
    this.latencyMs = metric.latencyMs();
    this.shortCode = metric.shortCode();
    this.userId = metric.userId();
    this.traceId = metric.traceId();
  }
}
