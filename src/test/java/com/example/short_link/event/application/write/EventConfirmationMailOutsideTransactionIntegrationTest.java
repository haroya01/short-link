package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.example.short_link.common.mail.MailSender;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the test asks whether a transaction is open while the mail goes out.
@SpringBootTest
@ActiveProfiles("test")
class EventConfirmationMailOutsideTransactionIntegrationTest {

  @MockitoBean private MailSender mail;
  @Autowired private RegisterForEventUseCase register;
  @Autowired private EventRepository events;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> sent = new CopyOnWriteArrayList<>();
  private Long ownerId;
  private EventEntity event;

  @BeforeEach
  void anOpenEventThatConfirmsByMail() {
    String name = "evt" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity owner = new UserEntity(name + "@example.com", "google", name);
    owner.claimUsername(name);
    ownerId = users.save(owner).getId();
    event =
        events.save(
            new EventEntity(
                ownerId,
                name,
                "스터디",
                null,
                Instant.now().plus(7, ChronoUnit.DAYS),
                null,
                "Asia/Seoul",
                null,
                null,
                null,
                null,
                null,
                ContactField.EMAIL));
    doAnswer(
            inv -> {
              sent.add(inv.getArgument(0) + " " + where());
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void deleteTheEvent() {
    jdbc.update("DELETE FROM event_registration WHERE event_id = ?", event.getId());
    jdbc.update("DELETE FROM event WHERE id = ?", event.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
  }

  @Test
  void theConfirmationGoesOutAfterTheRegistrationCommits() {
    register.execute(
        new RegisterForEventCommand(
            event.getSlug(), "참가자", "guest@example.com", Map.of(), "203.0.113.7", "test"));

    assertThat(sent).containsExactly("guest@example.com outside");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_registration WHERE event_id = ?",
                Long.class,
                event.getId()))
        .isEqualTo(1);
  }
}
