package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.write.BulkDeleteLinksCommand;
import com.example.short_link.link.application.write.BulkDeleteLinksUseCase;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.application.write.DeleteLinkCommand;
import com.example.short_link.link.application.write.DeleteLinkUseCase;
import com.example.short_link.link.application.write.UpdateLinkCommand;
import com.example.short_link.link.application.write.UpdateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import com.example.short_link.support.ConnectionsHeld;
import com.example.short_link.support.FailingSql;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Not @Transactional: a surrounding test transaction would hide both the second connection and
// what survives the owner's commit.
@SpringBootTest
@ActiveProfiles("test")
@Import({FailingSql.Config.class, ConnectionsHeld.Config.class})
@Tag("integration")
class AuditAfterCommitIntegrationTest {

  @MockitoBean private UrlSafetyChecker safety;

  @Autowired private FailingSql sql;
  @Autowired private ConnectionsHeld connections;
  @Autowired private CreateLinkUseCase createLink;
  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private DeleteLinkUseCase deleteLink;
  @Autowired private BulkDeleteLinksUseCase deleteLinks;
  @Autowired private UserDeletionService accounts;
  @Autowired private LinkRepository links;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private String suffix;

  @BeforeEach
  void anOwner() {
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity("au" + suffix + "@example.com", "google", "au" + suffix);
    user.claimUsername("au" + suffix);
    userId = users.save(user).getId();
    when(safety.isSafe(anyString())).thenReturn(true);
    connections.reset();
  }

  @AfterEach
  void deleteTheOwnersRows() {
    sql.reset();
    jdbc.update("DELETE FROM audit_log WHERE actor_user_id = ?", userId);
    jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private String aLink(String tag) {
    String code = (tag + suffix).substring(0, 7);
    links.save(new LinkEntity("https://example.com/" + tag + "-" + suffix, code, userId, null));
    connections.reset();
    return code;
  }

  private void auditRowsCannotBeWritten() {
    sql.refuse(
        "insert into audit_log",
        () -> new SQLException("Data too long for column 'request_id' at row 1", "22001", 1406));
  }

  private int auditRows(String action) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? AND action = ?",
        Integer.class,
        userId,
        action);
  }

  private int linksOwned() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM link WHERE user_id = ?", Integer.class, userId);
  }

  private String noteOf(String code) {
    return jdbc.queryForObject("SELECT note FROM link WHERE short_code = ?", String.class, code);
  }

  private UpdateLinkCommand aNewNote(String code) {
    return new UpdateLinkCommand(userId, new ShortCode(code), null, null, "바뀐 메모", null);
  }

  @Test
  void creatingALinkHoldsOneConnectionAtATime() {
    createLink.execute(
        CreateLinkCommand.of("https://example.com/new-" + suffix, userId, null, null));

    assertThat(connections.mostAtOnceOnThisThread()).isEqualTo(1);
    assertThat(auditRows("LINK_CREATED")).isEqualTo(1);
  }

  @Test
  void changingALinkHoldsOneConnectionAtATime() {
    String code = aLink("up");

    updateLink.execute(aNewNote(code));

    assertThat(connections.mostAtOnceOnThisThread()).isEqualTo(1);
    assertThat(auditRows("LINK_UPDATED")).isEqualTo(1);
  }

  @Test
  void deletingALinkHoldsOneConnectionAtATime() {
    String code = aLink("de");

    deleteLink.execute(new DeleteLinkCommand(userId, new ShortCode(code)));

    assertThat(connections.mostAtOnceOnThisThread()).isEqualTo(1);
    assertThat(auditRows("LINK_DELETED")).isEqualTo(1);
  }

  @Test
  void deletingSeveralLinksHoldsOneConnectionAtATime() {
    List<String> codes = List.of(aLink("b1"), aLink("b2"), aLink("b3"));

    deleteLinks.execute(BulkDeleteLinksCommand.of(userId, codes));

    assertThat(connections.mostAtOnceOnThisThread()).isEqualTo(1);
    assertThat(auditRows("LINK_DELETED")).isEqualTo(3);
  }

  @Test
  void closingAnAccountHoldsOneConnectionAtATime() {
    accounts.deleteAccount(userId);

    assertThat(connections.mostAtOnceOnThisThread()).isEqualTo(1);
    assertThat(auditRows("USER_DELETED")).isEqualTo(1);
  }

  @Test
  void aLinkIsCreatedEvenWhenItsAuditRowCannotBeWritten() {
    auditRowsCannotBeWritten();

    createLink.execute(
        CreateLinkCommand.of("https://example.com/new-" + suffix, userId, null, null));

    assertThat(sql.refused()).hasSize(1);
    assertThat(linksOwned()).isEqualTo(1);
  }

  @Test
  void aLinkKeepsItsChangeEvenWhenItsAuditRowCannotBeWritten() {
    String code = aLink("up");
    auditRowsCannotBeWritten();

    updateLink.execute(aNewNote(code));

    assertThat(sql.refused()).hasSize(1);
    assertThat(noteOf(code)).isEqualTo("바뀐 메모");
  }

  @Test
  void aLinkIsDeletedEvenWhenItsAuditRowCannotBeWritten() {
    String code = aLink("de");
    auditRowsCannotBeWritten();

    deleteLink.execute(new DeleteLinkCommand(userId, new ShortCode(code)));

    assertThat(sql.refused()).hasSize(1);
    assertThat(linksOwned()).isZero();
  }

  @Test
  void severalLinksAreDeletedEvenWhenTheirAuditRowsCannotBeWritten() {
    List<String> codes = List.of(aLink("b1"), aLink("b2"), aLink("b3"));
    auditRowsCannotBeWritten();

    assertThat(deleteLinks.execute(BulkDeleteLinksCommand.of(userId, codes))).isEqualTo(3);

    assertThat(sql.refused()).hasSize(3);
    assertThat(linksOwned()).isZero();
  }

  @Test
  void anAccountIsClosedEvenWhenItsAuditRowCannotBeWritten() {
    auditRowsCannotBeWritten();

    accounts.deleteAccount(userId);

    assertThat(sql.refused()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                userId))
        .isEqualTo(1);
  }

  @Test
  void aChangeThatNeverCommittedLeavesNoAuditRow() {
    String code = aLink("up");
    sql.refuse(
        "update link set",
        () -> new SQLException("Got error 1 from storage engine", "HY000", 1030));

    assertThatThrownBy(() -> updateLink.execute(aNewNote(code)))
        .isInstanceOf(RuntimeException.class);

    assertThat(noteOf(code)).isNull();
    assertThat(auditRows("LINK_UPDATED")).isZero();
  }
}
