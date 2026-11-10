package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.event.application.helper.CancelTokens;
import com.example.short_link.event.application.helper.EventSlugs;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class EventPiiPurgeIntegrationTest {

  @Autowired private PurgeEventPiiUseCase purge;
  @Autowired private EventRepository events;
  @Autowired private EventRegistrationRepository registrations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private Long eventEndedDaysAgo(Instant now, int days) {
    Instant ends = now.minus(days, ChronoUnit.DAYS);
    return events
        .save(
            new EventEntity(
                1L,
                EventSlugs.generate(),
                "스터디",
                null,
                ends.minusSeconds(3600),
                ends,
                "Asia/Seoul",
                null,
                null,
                null,
                null,
                null,
                ContactField.EMAIL))
        .getId();
  }

  private void register(Long eventId, String name, String contact) {
    registrations.save(
        new EventRegistrationEntity(
            eventId,
            name,
            contact,
            "{\"1\":\"" + contact + "\"}",
            CancelTokens.hash(CancelTokens.generate())));
    entityManager.flush();
  }

  private List<Map<String, Object>> storedPii(Long eventId) {
    return jdbc.queryForList(
        "SELECT name, contact, answers_json FROM event_registration WHERE event_id = ?", eventId);
  }

  @Test
  void registrationsStoredAfterAPurgeArePurgedOnTheNextRun() {
    Instant now = Instant.now();
    Long eventId = eventEndedDaysAgo(now, 31);
    register(eventId, "첫 신청자", "first@x.com");

    assertThat(purge.execute(now)).isGreaterThanOrEqualTo(1);
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM event WHERE id = ? AND pii_purged_at IS NOT NULL",
                Long.class,
                eventId))
        .isEqualTo(1L);

    register(eventId, "늦은 신청자", "late@x.com");
    register(eventId, "[purged]", "named-like-a-purged-row@x.com");
    entityManager.clear();

    assertThat(purge.execute(now)).isGreaterThanOrEqualTo(2);

    assertThat(storedPii(eventId))
        .hasSize(3)
        .allSatisfy(
            row -> {
              assertThat(row.get("name")).isEqualTo("[purged]");
              assertThat((String) row.get("contact")).startsWith("purged:");
              assertThat(row.get("answers_json")).isNull();
            });
    assertThat(events.findPurgeCandidates(now.minus(PurgeEventPiiUseCase.RETENTION), 100))
        .extracting(EventEntity::getId)
        .doesNotContain(eventId);
  }

  @Test
  void eventsInsideTheRetentionWindowKeepTheirRegistrations() {
    Instant now = Instant.now();
    Long eventId = eventEndedDaysAgo(now, 29);
    register(eventId, "신청자", "recent@x.com");

    purge.execute(now);
    entityManager.clear();

    assertThat(storedPii(eventId))
        .singleElement()
        .satisfies(row -> assertThat(row.get("contact")).isEqualTo("recent@x.com"));
  }
}
