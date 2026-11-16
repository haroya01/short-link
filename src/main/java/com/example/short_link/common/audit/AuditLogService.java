package com.example.short_link.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Callers record after their own transaction has committed, so a failed write is logged and
// swallowed. Inside a transaction the row joins it, and swallowing would leave it rollback-only.
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

  public void record(
      AuditAction action,
      String targetType,
      String targetId,
      Long actorUserId,
      Map<String, ?> metadata) {
    if (aFailureWouldLeaveTheCallersTransactionRollbackOnly()) {
      write(action, targetType, targetId, actorUserId, metadata);
      return;
    }
    try {
      write(action, targetType, targetId, actorUserId, metadata);
    } catch (RuntimeException e) {
      log.warn(
          "audit log write failed for action={} target={}/{}", action, targetType, targetId, e);
      meterRegistry.counter("audit.failed", "action", action.name()).increment();
    }
  }

  private static boolean aFailureWouldLeaveTheCallersTransactionRollbackOnly() {
    return TransactionSynchronizationManager.isActualTransactionActive();
  }

  private void write(
      AuditAction action,
      String targetType,
      String targetId,
      Long actorUserId,
      Map<String, ?> metadata) {
    repository.save(
        AuditLogEntity.record(
            actorUserId,
            action.name(),
            targetType,
            targetId,
            json(metadata),
            MDC.get("requestId"),
            Instant.now()));
    meterRegistry.counter("audit.recorded", "action", action.name()).increment();
  }

  private static String json(Map<String, ?> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("audit metadata cannot be written as JSON", e);
    }
  }
}
