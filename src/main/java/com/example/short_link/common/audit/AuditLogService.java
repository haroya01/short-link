package com.example.short_link.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records auditable events (link create/update/delete, account delete, admin actions). Each call
 * runs in its own transaction so a rollback in the caller doesn't lose the audit row, and a write
 * failure here doesn't cascade into the caller's flow (the call is logged-and-swallowed).
 */
@Slf4j
@Service
public class AuditLogService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AuditLogJpaRepository repository;
  private final MeterRegistry meterRegistry;

  public AuditLogService(AuditLogJpaRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
  }

  public void record(AuditAction action, String targetType, String targetId, Long actorUserId) {
    record(action, targetType, targetId, actorUserId, Map.of());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      AuditAction action,
      String targetType,
      String targetId,
      Long actorUserId,
      Map<String, ?> metadata) {
    try {
      String metadataJson =
          metadata == null || metadata.isEmpty()
              ? null
              : OBJECT_MAPPER.writeValueAsString(metadata);
      AuditLogEntity entity =
          AuditLogEntity.record(
              actorUserId,
              action.name(),
              targetType,
              targetId,
              metadataJson,
              MDC.get("requestId"),
              Instant.now());
      repository.save(entity);
      meterRegistry.counter("audit.recorded", "action", action.name()).increment();
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn(
          "audit log write failed for action={} target={}/{}", action, targetType, targetId, e);
      meterRegistry.counter("audit.failed", "action", action.name()).increment();
    }
  }
}
