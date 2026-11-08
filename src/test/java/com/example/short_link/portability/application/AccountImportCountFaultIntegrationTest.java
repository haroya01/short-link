package com.example.short_link.portability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.support.FailingSql;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingSql.Config.class)
class AccountImportCountFaultIntegrationTest {

  @Autowired private FailingSql sql;
  @Autowired private AccountImportService imports;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;

  @BeforeEach
  void aMember() {
    String name = "imp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    userId = users.save(user).getId();
  }

  @AfterEach
  void deleteTheMember() {
    sql.reset();
    jdbc.update("DELETE FROM account_import WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private static SQLException lockWaitTimeout() {
    return new SQLException("Lock wait timeout exceeded", "HY000", 1205);
  }

  private Long startAFollowingImport() {
    return imports.start(userId, "following", "ghost-one\nghost-two\n").id();
  }

  @Test
  void anImportWhoseCountsWereLostStillFinishesAndLetsTheMemberImportAgain() {
    Long first = startAFollowingImport();
    sql.refuse(
        "update account_import set processed_items",
        AccountImportCountFaultIntegrationTest::lockWaitTimeout);

    assertThatThrownBy(() -> imports.processBatch(AccountImportService.MAX_LINES));
    assertThat(sql.refused()).isNotEmpty();
    sql.reset();
    imports.processBatch(AccountImportService.MAX_LINES);

    assertThat(
            jdbc.queryForObject(
                "SELECT finished_at IS NOT NULL FROM account_import WHERE id = ?",
                Boolean.class,
                first))
        .isTrue();
    assertThatCode(this::startAFollowingImport).doesNotThrowAnyException();
  }

  @Test
  void anImportFinishesWithItsLastBatchAndCountsEveryLine() {
    Long id = startAFollowingImport();

    imports.processBatch(AccountImportService.MAX_LINES);

    assertThat(
            jdbc.queryForMap(
                "SELECT processed_items, imported_items, finished_at IS NOT NULL AS finished"
                    + " FROM account_import WHERE id = ?",
                id))
        .containsEntry("processed_items", 2)
        .containsEntry("imported_items", 0)
        .containsEntry("finished", 1L);
  }
}
