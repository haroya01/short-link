package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.repository.NotificationRoomReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRoomReaderIntegrationTest {

  @Autowired private NotificationRoomReader reader;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void aRoomIsNamedAsItsMembersNamedItAndAGoneRoomIsNot() {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at)"
            + " VALUES (1, '도쿄 모임', NOW(6), NOW(6))");
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    assertThat(reader.name(id)).contains("도쿄 모임");
    assertThat(reader.name(id + 1)).isEmpty();
  }
}
