package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogIndependentTransactionTest {
  @Autowired private AuditLogService auditLogService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private final String withoutMetadata = UUID.randomUUID().toString();
  private final String withMetadata = UUID.randomUUID().toString();

  @AfterEach
  void deleteCommittedRows() {
    for (String targetId : List.of(withoutMetadata, withMetadata)) {
      jdbc.update("DELETE FROM audit_log WHERE target_id = ?", targetId);
    }
  }

  @Test
  void bothOverloadsKeepTheAuditRowWhenTheCallerRollsBack() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              auditLogService.record(AuditAction.LINK_DELETED, "link", withoutMetadata, 1L);
              auditLogService.record(
                  AuditAction.LINK_DELETED, "link", withMetadata, 1L, Map.of("bulk", true));
              status.setRollbackOnly();
            });

    assertThat(rowsFor(withMetadata)).isEqualTo(1);
    assertThat(rowsFor(withoutMetadata)).isEqualTo(1);
  }

  private Integer rowsFor(String targetId) {
    return jdbc.queryForObject(
        "select count(*) from audit_log where target_id = ?", Integer.class, targetId);
  }
}
