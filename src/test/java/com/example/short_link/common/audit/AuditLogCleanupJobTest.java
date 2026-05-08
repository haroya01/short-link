package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogCleanupJobTest {

  @Autowired private AuditLogCleanupJob job;
  @Autowired private AuditLogRepository repository;

  @Test
  void deletesRowsBeyondRetention() {
    ReflectionTestUtils.setField(job, "retentionDays", 90L);
    Instant now = Instant.now();
    Long oldId =
        repository
            .save(
                AuditLogEntity.builder()
                    .actorUserId(1L)
                    .action("ADMIN_LOGIN")
                    .targetType("user")
                    .targetId("1")
                    .occurredAt(now.minus(Duration.ofDays(120)))
                    .build())
            .getId();
    Long recentId =
        repository
            .save(
                AuditLogEntity.builder()
                    .actorUserId(1L)
                    .action("LINK_EDIT")
                    .targetType("link")
                    .targetId("abc")
                    .occurredAt(now.minus(Duration.ofDays(30)))
                    .build())
            .getId();

    job.sweep();

    assertThat(repository.findById(oldId)).isEmpty();
    assertThat(repository.findById(recentId)).isPresent();
  }

  @Test
  void noopWhenAllRowsRecent() {
    ReflectionTestUtils.setField(job, "retentionDays", 90L);
    Long id =
        repository
            .save(
                AuditLogEntity.builder()
                    .actorUserId(2L)
                    .action("LINK_EDIT")
                    .targetType("link")
                    .targetId("xyz")
                    .occurredAt(Instant.now().minus(Duration.ofDays(10)))
                    .build())
            .getId();

    job.sweep();

    assertThat(repository.findById(id)).isPresent();
  }
}
