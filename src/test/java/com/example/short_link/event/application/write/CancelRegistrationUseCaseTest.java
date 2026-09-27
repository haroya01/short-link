package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.helper.CancelTokens;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelRegistrationUseCaseTest {

  @Mock private EventRegistrationRepository registrationRepository;
  @Mock private EventRepository eventRepository;
  @InjectMocks private CancelRegistrationUseCase useCase;

  @Test
  void cancel_confirmedRegistration_decrementsCount() {
    String token = CancelTokens.generate();
    EventRegistrationEntity registration =
        new EventRegistrationEntity(42L, "김철수", "a@b.com", null, CancelTokens.hash(token));
    when(registrationRepository.findByCancelTokenHash(CancelTokens.hash(token)))
        .thenReturn(Optional.of(registration));

    useCase.execute(token);

    assertThat(registration.isConfirmed()).isFalse();
    verify(eventRepository).decrementRegistrationCount(42L);
  }

  @Test
  void cancel_alreadyCanceled_isIdempotent() {
    String token = CancelTokens.generate();
    EventRegistrationEntity registration =
        new EventRegistrationEntity(42L, "김철수", "a@b.com", null, CancelTokens.hash(token));
    registration.cancel(Instant.now());
    when(registrationRepository.findByCancelTokenHash(CancelTokens.hash(token)))
        .thenReturn(Optional.of(registration));

    useCase.execute(token);

    verify(eventRepository, never()).decrementRegistrationCount(42L);
  }

  @Test
  void cancel_unknownToken_throws() {
    when(registrationRepository.findByCancelTokenHash(CancelTokens.hash("bogus")))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute("bogus")).isInstanceOf(EventException.class);
  }
}
