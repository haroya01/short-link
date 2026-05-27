package com.example.short_link.profile.application.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.profile.domain.email.EmailLeadEntity;
import com.example.short_link.profile.domain.email.EmailLeadRepository;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.support.TestEntities;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailLeadServiceTest {

  private EmailLeadRepository repository;
  private EmailLeadService service;

  @BeforeEach
  void setUp() {
    repository = mock(EmailLeadRepository.class);
    ProfileBlockRepository blockRepository = mock(ProfileBlockRepository.class);
    EmailLeadPageReader pageReader = mock(EmailLeadPageReader.class);
    service = new EmailLeadService(repository, blockRepository, pageReader, "");
  }

  @Test
  void setOptedOutFlipsFlagForOwner() {
    EmailLeadEntity lead = leadOwnedBy(7L, false);
    when(repository.findById(11L)).thenReturn(Optional.of(lead));
    when(repository.save(any(EmailLeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    EmailLeadEntity out = service.setOptedOut(7L, 11L, true);

    assertThat(out.isOptedOut()).isTrue();
    verify(repository).save(lead);
  }

  @Test
  void setOptedOutRejectsNonOwner() {
    when(repository.findById(11L)).thenReturn(Optional.of(leadOwnedBy(7L, false)));

    assertThatThrownBy(() -> service.setOptedOut(9L, 11L, true))
        .isInstanceOf(ProfileException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void setOptedOutMissingLead404s() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setOptedOut(7L, 99L, true))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void setOptedOutCanUnsetExistingFlag() {
    // Owner can re-include someone who was previously opted-out — e.g. user resubscribed via
    // the form again and the dashboard reflects it.
    EmailLeadEntity lead = leadOwnedBy(7L, true);
    when(repository.findById(11L)).thenReturn(Optional.of(lead));
    when(repository.save(any(EmailLeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    EmailLeadEntity out = service.setOptedOut(7L, 11L, false);

    assertThat(out.isOptedOut()).isFalse();
  }

  private static EmailLeadEntity leadOwnedBy(long userId, boolean optedOut) {
    EmailLeadEntity lead = new EmailLeadEntity(userId, 1L, "u@example.com", null);
    if (optedOut) {
      TestEntities.setField(lead, "optedOut", true);
    }
    return lead;
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException ex) {
      throw new RuntimeException(ex);
    }
  }
}
