package com.example.short_link.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.short_link.common.audit.AddressChangeHistory.AddressChanges;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AddressChangeHistoryIntegrationTest {

  @Autowired private AddressChangeHistory history;
  @Autowired private AuditLogService auditLog;
  @Autowired private JdbcTemplate jdbc;

  private final String code = "hist" + UUID.randomUUID().toString().substring(0, 8);
  private final String other = "hist" + UUID.randomUUID().toString().substring(0, 8);

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update(
        "DELETE FROM audit_log WHERE target_type = 'link' AND target_id IN (?, ?)", code, other);
  }

  @Test
  void onlyChangesOfWhereTheLinkGoesAreCountedAndTheLatestNamesItsHosts() {
    move(code, "https://old.example/a", "https://first.example/b");
    record(
        code,
        AuditAction.LINK_UPDATED,
        AddressChange.between("https://x.example", "https://x.example"));
    destination(code, AuditAction.LINK_DESTINATION_ADDED, null, "https://branch.example/c");
    destination(
        code,
        AuditAction.LINK_DESTINATION_UPDATED,
        "https://branch.example/c",
        "https://branch.example/c");
    record(code, AuditAction.LINK_CREATED, Map.of("urlChanged", true));
    move(other, "https://elsewhere.example", "https://another.example");
    move(code, "https://first.example/b", "https://latest.example/d");

    AddressChanges changes = history.ofLink(code);

    assertThat(changes.retentionDays()).isEqualTo(180);
    assertThat(changes.urlChanges()).isEqualTo(2);
    assertThat(changes.destinationChanges()).isEqualTo(1);
    assertThat(changes.latest().action()).isEqualTo("LINK_UPDATED");
    assertThat(changes.latest().fromHost()).isEqualTo("first.example");
    assertThat(changes.latest().toHost()).isEqualTo("latest.example");
    assertThat(changes.latest().at()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
  }

  @Test
  void aChangeOlderThanTheRetentionIsLeftOutEvenBeforeTheCleanupRuns() {
    move(code, "https://old.example/a", "https://first.example/b");
    jdbc.update(
        "UPDATE audit_log SET occurred_at = ? WHERE target_type = 'link' AND target_id = ?",
        LocalDateTime.ofInstant(Instant.now().minus(181, ChronoUnit.DAYS), ZoneOffset.UTC),
        code);

    assertThat(history.ofLink(code)).isEqualTo(new AddressChanges(180, 0, 0, null));
  }

  @Test
  void aChangeRecordedBeforeHostsWereKeptCountsWithoutHosts() {
    record(code, AuditAction.LINK_UPDATED, Map.of("urlChanged", true, "expiresAtChanged", false));

    AddressChanges changes = history.ofLink(code);

    assertThat(changes.urlChanges()).isEqualTo(1);
    assertThat(changes.latest().fromHost()).isNull();
    assertThat(changes.latest().toHost()).isNull();
  }

  private void move(String target, String from, String to) {
    record(target, AuditAction.LINK_UPDATED, AddressChange.between(from, to));
  }

  private void destination(String target, AuditAction action, String from, String to) {
    Map<String, Object> metadata = new HashMap<>(AddressChange.between(from, to));
    metadata.put("destinationId", 1L);
    record(target, action, metadata);
  }

  private void record(String target, AuditAction action, Map<String, ?> metadata) {
    auditLog.record(action, "link", target, 1L, metadata);
  }
}
