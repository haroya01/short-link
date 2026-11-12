package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class AuditLogCallerTransactionTest {
  private static final String LONGER_THAN_THE_TARGET_ID_COLUMN = "x".repeat(65);

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
  void bothOverloadsCommitTheirRowWhenNoTransactionIsOpen() {
    auditLogService.record(AuditAction.LINK_DELETED, "link", withoutMetadata, 1L);
    auditLogService.record(
        AuditAction.LINK_DELETED, "link", withMetadata, 1L, Map.of("bulk", true));

    assertThat(rowsFor(withMetadata)).isEqualTo(1);
    assertThat(rowsFor(withoutMetadata)).isEqualTo(1);
  }

  @Test
  void bothOverloadsShareTheFateOfTheCallersTransaction() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              auditLogService.record(AuditAction.LINK_DELETED, "link", withoutMetadata, 1L);
              auditLogService.record(
                  AuditAction.LINK_DELETED, "link", withMetadata, 1L, Map.of("bulk", true));
              status.setRollbackOnly();
            });

    assertThat(rowsFor(withMetadata)).isZero();
    assertThat(rowsFor(withoutMetadata)).isZero();
  }

  @Test
  void aRowThatCannotBeWrittenIsSwallowedWhenNoTransactionIsOpen() {
    assertThatCode(
            () ->
                auditLogService.record(
                    AuditAction.LINK_DELETED, "link", LONGER_THAN_THE_TARGET_ID_COLUMN, 1L))
        .doesNotThrowAnyException();
  }

  @Test
  void aRowThatCannotBeWrittenFailsTheCallersTransactionWithItsCause() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status ->
                        auditLogService.record(
                            AuditAction.LINK_DELETED,
                            "link",
                            LONGER_THAN_THE_TARGET_ID_COLUMN,
                            1L)))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("target_id");
  }

  @Test
  void theLongestRequestIdTheEntityAllowsIsStored() throws Exception {
    int column =
        AuditLogEntity.class.getDeclaredField("requestId").getAnnotation(Column.class).length();
    String longest = "r".repeat(column);
    MDC.put("requestId", longest);
    try {
      auditLogService.record(AuditAction.LINK_DELETED, "link", withoutMetadata, 1L);
    } finally {
      MDC.remove("requestId");
    }

    assertThat(
            jdbc.queryForObject(
                "select request_id from audit_log where target_id = ?",
                String.class,
                withoutMetadata))
        .isEqualTo(longest);
  }

  private Integer rowsFor(String targetId) {
    return jdbc.queryForObject(
        "select count(*) from audit_log where target_id = ?", Integer.class, targetId);
  }
}
