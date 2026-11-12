package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.support.FailingSql;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingSql.Config.class)
@Tag("integration")
class RegistrationAttributionFaultIntegrationTest {

  private static final String IP = "203.0.113.7";
  private static final String UA = "Mozilla/5.0 (iPhone) KAKAOTALK";

  @Autowired private FailingSql sql;
  @Autowired private RegisterForEventUseCase register;
  @Autowired private EventRepository events;
  @Autowired private EventLinkRepository eventLinks;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long ownerId;
  private EventEntity event;
  private long linkId;

  @BeforeEach
  void anOpenEventWithATrackedLink() {
    String name = "atr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
    linkId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    eventLinks.save(new EventLinkEntity(event.getId(), linkId, "기본"));
  }

  @AfterEach
  void deleteTheEvent() {
    sql.reset();
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM event_registration WHERE event_id = ?", event.getId());
    jdbc.update("DELETE FROM event_link WHERE event_id = ?", event.getId());
    jdbc.update("DELETE FROM event WHERE id = ?", event.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
  }

  private RegistrationResult registerFromTheLink() {
    return register.execute(
        new RegisterForEventCommand(event.getSlug(), "참가자", "guest@example.com", Map.of(), IP, UA));
  }

  private static SQLException storageEngineError() {
    return new SQLException("Got error 1 from storage engine", "HY000", 1030);
  }

  private void aHumanClickFromTheSameVisitor() {
    jdbc.update(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, visitor_hash, source_channel,"
            + " client_app, referrer_host, utm_source) VALUES (?, NOW(), 0, ?, 'messenger',"
            + " 'kakaotalk', 'kakao.example', 'kw')",
        linkId,
        VisitorHasher.hash(linkId, IP, UA));
  }

  private Map<String, Object> theRegistration() {
    return jdbc.queryForMap(
        "SELECT r.status, r.link_id, r.source_channel, r.client_app, r.referrer_host,"
            + " r.utm_source, r.visitor_hash, e.registration_count"
            + " FROM event_registration r JOIN event e ON e.id = r.event_id WHERE r.event_id = ?",
        event.getId());
  }

  @Test
  void aMatchingClickStampsTheChannelOnTheCommittedRegistration() {
    aHumanClickFromTheSameVisitor();

    RegistrationResult result = registerFromTheLink();

    assertThat(result.registrationId()).isNotNull();
    assertThat(theRegistration())
        .containsEntry("status", "CONFIRMED")
        .containsEntry("link_id", linkId)
        .containsEntry("source_channel", "messenger")
        .containsEntry("client_app", "kakaotalk")
        .containsEntry("referrer_host", "kakao.example")
        .containsEntry("utm_source", "kw")
        .containsEntry("visitor_hash", VisitorHasher.hash(linkId, IP, UA))
        .containsEntry("registration_count", 1);
  }

  @Test
  void aFailedEventLinkReadKeepsTheRegistrationWithoutAChannel() {
    aHumanClickFromTheSameVisitor();
    sql.refuse("from event_link", RegistrationAttributionFaultIntegrationTest::storageEngineError);

    RegistrationResult result = registerFromTheLink();

    assertThat(sql.refused()).isNotEmpty();
    assertThat(result.registrationId()).isNotNull();
    assertThat(theRegistration())
        .containsEntry("status", "CONFIRMED")
        .containsEntry("link_id", null)
        .containsEntry("registration_count", 1);
  }

  @Test
  void aFailedClickLookupKeepsTheRegistrationWithoutAChannel() {
    aHumanClickFromTheSameVisitor();
    sql.refuse("from click_event", RegistrationAttributionFaultIntegrationTest::storageEngineError);

    RegistrationResult result = registerFromTheLink();

    assertThat(sql.refused()).isNotEmpty();
    assertThat(result.registrationId()).isNotNull();
    assertThat(theRegistration())
        .containsEntry("status", "CONFIRMED")
        .containsEntry("link_id", null)
        .containsEntry("registration_count", 1);
  }

  @Test
  void aFailedChannelWriteKeepsTheRegistrationWithoutAChannel() {
    aHumanClickFromTheSameVisitor();
    sql.refuse(
        "update event_registration",
        RegistrationAttributionFaultIntegrationTest::storageEngineError);

    RegistrationResult result = registerFromTheLink();

    assertThat(sql.refused()).isNotEmpty();
    assertThat(result.registrationId()).isNotNull();
    assertThat(theRegistration())
        .containsEntry("status", "CONFIRMED")
        .containsEntry("link_id", null)
        .containsEntry("registration_count", 1);
  }
}
