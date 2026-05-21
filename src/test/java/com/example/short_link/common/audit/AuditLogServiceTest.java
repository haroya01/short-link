package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.queryaudit.junit5.QueryAudit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@QueryAudit
class AuditLogServiceTest {

  @Autowired private AuditLogService auditLogService;
  @Autowired private AuditLogRepository auditLogRepository;

  @Test
  void persistsAuditRowWithMetadata() {
    auditLogService.record(
        AuditAction.LINK_CREATED,
        "link",
        "abc1234",
        42L,
        Map.of("custom", true, "anonymous", false));

    List<AuditLogEntity> all = auditLogRepository.findAll();
    AuditLogEntity recorded =
        all.stream().filter(a -> "abc1234".equals(a.getTargetId())).findFirst().orElseThrow();
    assertThat(recorded.getAction()).isEqualTo("LINK_CREATED");
    assertThat(recorded.getTargetType()).isEqualTo("link");
    assertThat(recorded.getActorUserId()).isEqualTo(42L);
    assertThat(recorded.getMetadata()).contains("\"custom\"");
    assertThat(recorded.getMetadata()).contains("true");
    assertThat(recorded.getOccurredAt()).isNotNull();

    auditLogRepository.deleteAll();
  }

  @Test
  void persistsWithoutMetadata() {
    auditLogService.record(AuditAction.USER_DELETED, "user", "99", 99L);

    List<AuditLogEntity> all = auditLogRepository.findAll();
    AuditLogEntity recorded =
        all.stream().filter(a -> "99".equals(a.getTargetId())).findFirst().orElseThrow();
    assertThat(recorded.getMetadata()).isNull();

    auditLogRepository.deleteAll();
  }
}
