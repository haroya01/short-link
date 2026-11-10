package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class LinkSidecarsDeferToLinkMigrationTest {

  private static final String PREVIOUS_IMAGE_READ =
      """
      SELECT
        COALESCE(acl.password_hash, l.password_hash) IS NOT NULL AS password_required,
        COALESCE(acl.max_views, l.max_views) AS max_views,
        COALESCE(policy.blocked_countries, l.blocked_countries) AS blocked_countries,
        COALESCE(policy.expired_message, l.expired_message) AS expired_message,
        COALESCE(og.og_title, l.og_title) AS og_title
      FROM link l
      LEFT JOIN link_access_control acl ON acl.link_id = l.id
      LEFT JOIN link_expiration_policy policy ON policy.link_id = l.id
      LEFT JOIN link_og_metadata og ON og.link_id = l.id
      WHERE l.id = ?
      """;

  @Autowired private LinkRepository links;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void aLinkWhoseSidecarsDisagree() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "m" + suffix, owner.getId(), null));
    links.recordOgFetched(link.getId(), "link title", null, null, Instant.now());
    jdbc.update(
        "UPDATE link SET max_views = 5, blocked_countries = 'JP' WHERE id = ?", link.getId());
    jdbc.update(
        "INSERT INTO link_access_control (link_id, password_hash, max_views, created_at,"
            + " updated_at) VALUES (?, ?, 1, NOW(6), NOW(6))",
        link.getId(),
        "$2a$10$" + "s".repeat(53));
    jdbc.update(
        "INSERT INTO link_expiration_policy (link_id, blocked_countries, expired_message,"
            + " created_at, updated_at) VALUES (?, 'KR', 'sidecar message', NOW(6), NOW(6))",
        link.getId());
    jdbc.update(
        "INSERT INTO link_og_metadata (link_id, og_title, og_fetch_status, og_fetch_attempts,"
            + " created_at, updated_at) VALUES (?, 'sidecar title', 'OK', 1, NOW(6), NOW(6))",
        link.getId());
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  @Test
  void afterTheMigrationThePreviousImageReadsTheLinkRow() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V217__link_sidecars_defer_to_link.sql"));
    }

    Map<String, Object> read = jdbc.queryForMap(PREVIOUS_IMAGE_READ, link.getId());

    assertThat(read)
        .containsEntry("password_required", 0L)
        .containsEntry("max_views", 5)
        .containsEntry("blocked_countries", "JP")
        .containsEntry("expired_message", null)
        .containsEntry("og_title", "link title");
  }
}
