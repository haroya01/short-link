package com.example.short_link.common.audit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.audit.AuditLogEntity;
import com.example.short_link.common.audit.AuditLogJpaRepository;
import com.example.short_link.common.audit.AuditLogProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogCleanupJobTest {

  @Autowired private AuditLogCleanupJob job;
  @Autowired private AuditLogJpaRepository repository;

  @Test
  void deletesRowsBeyondRetention() {
    ReflectionTestUtils.setField(job, "auditLog", new AuditLogProperties(90L, true));
    Instant now = Instant.now();
    Long oldId =
        repository
            .save(
                AuditLogEntity.record(
                    1L, "ADMIN_LOGIN", "user", "1", null, null, now.minus(Duration.ofDays(120))))
            .getId();
    Long recentId =
        repository
            .save(
                AuditLogEntity.record(
                    1L, "LINK_EDIT", "link", "abc", null, null, now.minus(Duration.ofDays(30))))
            .getId();

    job.sweep();

    assertThat(repository.findById(oldId)).isEmpty();
    assertThat(repository.findById(recentId)).isPresent();
  }

  @Test
  void noopWhenAllRowsRecent() {
    ReflectionTestUtils.setField(job, "auditLog", new AuditLogProperties(90L, true));
    Long id =
        repository
            .save(
                AuditLogEntity.record(
                    2L,
                    "LINK_EDIT",
                    "link",
                    "xyz",
                    null,
                    null,
                    Instant.now().minus(Duration.ofDays(10))))
            .getId();

    job.sweep();

    assertThat(repository.findById(id)).isPresent();
  }

  // Reproduces the production scheduler context: @Scheduled invokes runDaily() with no ambient
  // transaction. Without @Transactional on the entry point, sweep()'s self-invocation bypasses
  // the proxy and flush blows up with "No EntityManager with actual transaction available".
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void runDailyOpensOwnTransactionForSweep() {
    ReflectionTestUtils.setField(job, "auditLog", new AuditLogProperties(90L, true));
    Long oldId =
        repository
            .save(
                AuditLogEntity.record(
                    7L,
                    "RUN_DAILY_PROBE",
                    "probe",
                    "p1",
                    null,
                    null,
                    Instant.now().minus(Duration.ofDays(200))))
            .getId();
    try {
      job.runDaily();
      assertThat(repository.findById(oldId)).isEmpty();
    } finally {
      repository.findById(oldId).ifPresent(repository::delete);
    }
  }
}
