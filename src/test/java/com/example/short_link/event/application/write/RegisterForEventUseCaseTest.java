package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.RegistrationAttributor;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegisterForEventUseCaseTest {

  @Mock private EventRepository eventRepository;
  @Mock private EventQuestionRepository questionRepository;
  @Mock private EventRegistrationRepository registrationRepository;
  @Mock private RegistrationAttributor attributor;
  @Mock private RegistrationMailer mailer;

  private RegisterForEventUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RegisterForEventUseCase(
            eventRepository,
            questionRepository,
            registrationRepository,
            attributor,
            mailer,
            new SimpleMeterRegistry());
  }

  private EventEntity openEvent(Integer capacity) {
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            Instant.now().plus(7, ChronoUnit.DAYS),
            null,
            "Asia/Tokyo",
            null,
            null,
            null,
            capacity,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", 42L);
    return event;
  }

  private RegisterForEventCommand command(String contact) {
    return new RegisterForEventCommand("slug123abc", "김철수", contact, null, "1.2.3.4", "UA");
  }

  @Test
  void register_happyPath() {
    EventEntity event = openEvent(10);
    when(eventRepository.findBySlug("slug123abc")).thenReturn(Optional.of(event));
    when(questionRepository.findAllByEventIdOrderByPosition(42L)).thenReturn(List.of());
    when(registrationRepository.findByEventIdAndContact(42L, "a@b.com"))
        .thenReturn(Optional.empty());
    when(eventRepository.tryIncrementRegistrationCount(42L)).thenReturn(1);
    when(registrationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              EventRegistrationEntity saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 7L);
              return saved;
            });

    RegistrationResult result = useCase.execute(command("A@B.com"));

    assertThat(result.registrationId()).isEqualTo(7L);
    assertThat(result.cancelToken()).hasSize(32);
    verify(attributor).attribute(any(), any(), any(), any());
    verify(mailer).sendConfirmation(any(), any(), any());
  }

  @Test
  void register_fullEvent_throwsAndSkipsSave() {
    EventEntity event = openEvent(1);
    when(eventRepository.findBySlug("slug123abc")).thenReturn(Optional.of(event));
    when(questionRepository.findAllByEventIdOrderByPosition(42L)).thenReturn(List.of());
    when(registrationRepository.findByEventIdAndContact(42L, "a@b.com"))
        .thenReturn(Optional.empty());
    when(eventRepository.tryIncrementRegistrationCount(42L)).thenReturn(0);

    assertThatThrownBy(() -> useCase.execute(command("a@b.com")))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_FULL);
    verify(registrationRepository, never()).save(any());
  }

  @Test
  void register_duplicateConfirmedContact_rejected() {
    EventEntity event = openEvent(null);
    EventRegistrationEntity existing =
        new EventRegistrationEntity(42L, "김철수", "a@b.com", null, "hash");
    when(eventRepository.findBySlug("slug123abc")).thenReturn(Optional.of(event));
    when(questionRepository.findAllByEventIdOrderByPosition(42L)).thenReturn(List.of());
    when(registrationRepository.findByEventIdAndContact(42L, "a@b.com"))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(command("a@b.com")))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.ALREADY_REGISTERED);
  }

  @Test
  void register_canceledContact_reactivated() {
    EventEntity event = openEvent(null);
    EventRegistrationEntity canceled =
        new EventRegistrationEntity(42L, "김철수", "a@b.com", null, "old-hash");
    canceled.cancel(Instant.now());
    when(eventRepository.findBySlug("slug123abc")).thenReturn(Optional.of(event));
    when(questionRepository.findAllByEventIdOrderByPosition(42L)).thenReturn(List.of());
    when(registrationRepository.findByEventIdAndContact(42L, "a@b.com"))
        .thenReturn(Optional.of(canceled));
    when(eventRepository.tryIncrementRegistrationCount(42L)).thenReturn(1);
    when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    useCase.execute(command("a@b.com"));

    assertThat(canceled.isConfirmed()).isTrue();
    assertThat(canceled.getCanceledAt()).isNull();
  }

  @Test
  void register_closedEvent_rejected() {
    EventEntity event = openEvent(null);
    event.close();
    when(eventRepository.findBySlug("slug123abc")).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> useCase.execute(command("a@b.com")))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_REGISTRATION_CLOSED);
  }
}
