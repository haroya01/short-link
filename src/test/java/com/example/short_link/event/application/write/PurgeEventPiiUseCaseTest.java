package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PurgeEventPiiUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-15T04:20:00Z");

  @Mock private EventRepository eventRepository;
  @Mock private EventRegistrationRepository registrationRepository;

  private PurgeEventPiiUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new PurgeEventPiiUseCase(eventRepository, registrationRepository);
  }

  private EventEntity endedEvent(long id) {
    EventEntity event =
        new EventEntity(
            1L,
            "slug" + id + "abcde",
            "스터디",
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            null,
            "Asia/Seoul",
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", id);
    return event;
  }

  @Test
  void noCandidates_purgesNothing() {
    when(eventRepository.findPurgeCandidates(NOW.minus(PurgeEventPiiUseCase.RETENTION), 100))
        .thenReturn(List.of());

    assertThat(useCase.execute(NOW)).isZero();
  }

  @Test
  void candidates_arePurgedAndStamped() {
    EventEntity first = endedEvent(10L);
    EventEntity second = endedEvent(11L);
    when(eventRepository.findPurgeCandidates(NOW.minus(PurgeEventPiiUseCase.RETENTION), 100))
        .thenReturn(List.of(first, second));
    when(registrationRepository.purgePiiByEventId(10L)).thenReturn(3);
    when(registrationRepository.purgePiiByEventId(11L)).thenReturn(0);

    assertThat(useCase.execute(NOW)).isEqualTo(3);
    assertThat(first.getPiiPurgedAt()).isEqualTo(NOW);
    assertThat(second.getPiiPurgedAt()).isEqualTo(NOW);
  }
}
