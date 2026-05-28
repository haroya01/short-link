package com.example.short_link.common.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuditLogServiceFailureTest {

  @Test
  void noMetadataOverloadDelegatesToMapVariant() {
    AuditLogJpaRepository repo = Mockito.mock(AuditLogJpaRepository.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    AuditLogService service = new AuditLogService(repo, meters);

    service.record(AuditAction.LINK_DELETED, "LINK", "abc1234", 42L);

    verify(repo).save(any(AuditLogEntity.class));
    assertCountAtLeastOne(meters, "audit.recorded", "action", AuditAction.LINK_DELETED.name());
  }

  @Test
  void repositoryFailureIsSwallowed_metricIncrementsFailed() {
    AuditLogJpaRepository repo = Mockito.mock(AuditLogJpaRepository.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    AuditLogService service = new AuditLogService(repo, meters);
    when(repo.save(any(AuditLogEntity.class))).thenThrow(new RuntimeException("db down"));

    // 절대 예외 안 던져야 — caller flow 보호
    service.record(AuditAction.LINK_DELETED, "LINK", "abc1234", 42L, Map.of("k", "v"));

    assertCountAtLeastOne(meters, "audit.failed", "action", AuditAction.LINK_DELETED.name());
  }

  @Test
  void emptyMetadataMapPersistsAsNull() {
    AuditLogJpaRepository repo = Mockito.mock(AuditLogJpaRepository.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    AuditLogService service = new AuditLogService(repo, meters);

    service.record(AuditAction.LINK_CREATED, "LINK", "abc1234", 1L, Map.of());

    verify(repo).save(any(AuditLogEntity.class));
    verify(repo, never())
        .save(
            Mockito.argThat(
                e ->
                    e == null
                        || (e != null && e.getMetadata() != null && !e.getMetadata().isEmpty())));
  }

  private static void assertCountAtLeastOne(
      SimpleMeterRegistry meters, String name, String tagKey, String tagValue) {
    double count = meters.counter(name, tagKey, tagValue).count();
    if (count < 1.0) throw new AssertionError(name + " expected ≥1 but was " + count);
  }
}
