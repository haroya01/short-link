package com.example.short_link.live.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.live.application.LiveBus;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class LiveSignalTransactionIntegrationTest {

  @Autowired private ApplicationEventPublisher events;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private LiveBus bus;

  private TransactionTemplate transactions;
  private String email;

  @BeforeEach
  void prepare() {
    transactions = new TransactionTemplate(transactionManager);
    email = "live-" + UUID.randomUUID() + "@x.com";
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM users WHERE email = ?", email);
  }

  private LiveChange writeAndSignal() {
    users.save(new UserEntity(email, "google", email));
    LiveChange change = new LiveChange(LiveTopic.room(4L), LiveChange.Kind.NOTE, 9L, 4L);
    events.publishEvent(change);
    return change;
  }

  @Test
  void theBusHearsOnlyOnceTheWriteIsVisibleToOtherConnections() {
    List<Object> seen = new ArrayList<>();
    doAnswer(
            invocation -> {
              try (Connection other = dataSource.getConnection();
                  PreparedStatement count =
                      other.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
                count.setString(1, email);
                try (ResultSet rows = count.executeQuery()) {
                  rows.next();
                  seen.add(rows.getLong(1));
                }
              }
              return null;
            })
        .when(bus)
        .publish(any(LiveChange.class));

    LiveChange change = transactions.execute(status -> writeAndSignal());

    verify(bus).publish(change);
    assertThat(seen).containsExactly(1L);
  }

  @Test
  void aRolledBackWriteSignalsNothing() {
    transactions.executeWithoutResult(
        status -> {
          writeAndSignal();
          events.publishEvent(new LiveRevoke.LeftRoom(4L, 2L));
          status.setRollbackOnly();
        });

    verify(bus, never()).publish(any(LiveChange.class));
    verify(bus, never()).publish(any(LiveRevoke.class));
  }

  @Test
  void aSignalOutsideAnyTransactionIsNotSent() {
    events.publishEvent(new LiveChange(LiveTopic.room(4L), LiveChange.Kind.NOTE, 9L, 4L));

    verify(bus, never()).publish(any(LiveChange.class));
  }
}
