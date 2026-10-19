package com.example.short_link.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FollowNotifyNotesMigrationIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository userRepository;

  private final List<Long> users = new ArrayList<>();

  private long user(String handle) {
    UserEntity u = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    u.claimUsername(handle);
    long id = userRepository.save(u).getId();
    users.add(id);
    return id;
  }

  @AfterEach
  void tearDown() {
    for (Long id : users) {
      jdbc.update("DELETE FROM user_follow WHERE follower_id = ? OR following_id = ?", id, id);
    }
    for (Long id : users) {
      jdbc.update("DELETE FROM users WHERE id = ?", id);
    }
  }

  private boolean bell(long follower, long following) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT notify_notes FROM user_follow WHERE follower_id = ? AND following_id = ?",
            Boolean.class,
            follower,
            following));
  }

  @Test
  void followsMadeBeforeTurnOnAndNewRowsStartOn() throws Exception {
    long writer = user("fnm-writer");
    long quiet = user("fnm-quiet");
    long later = user("fnm-later");
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at, notify_notes)"
            + " VALUES (?, ?, NOW(6), FALSE)",
        quiet,
        writer);

    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V194__follow_notify_notes_default.sql"));
    }
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        later,
        writer);

    assertThat(bell(quiet, writer)).isTrue();
    assertThat(bell(later, writer)).isTrue();
  }
}
