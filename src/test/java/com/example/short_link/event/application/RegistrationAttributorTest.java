package com.example.short_link.event.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.RegistrationClickLookup.ClickSnapshot;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationAttributorTest {

  @Mock private EventLinkRepository eventLinkRepository;
  @Mock private RegistrationClickLookup clickLookup;
  @Mock private EventRegistrationRepository registrationRepository;

  private RegistrationAttributor attributor;

  @BeforeEach
  void setUp() {
    attributor =
        new RegistrationAttributor(eventLinkRepository, clickLookup, registrationRepository);
  }

  @Test
  void eventWithoutLinks_skipsLookup() {
    when(eventLinkRepository.findAllByEventId(10L)).thenReturn(List.of());

    attributor.attribute(10L, 5L, "1.2.3.4", "Mozilla/5.0");

    verify(clickLookup, never()).findLatestHumanClick(anyList());
    verifyNoInteractions(registrationRepository);
  }

  @Test
  void matchedClick_recordsChannelSnapshotAndVisitorHash() {
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(List.of(new EventLinkEntity(10L, 7L, "기본")));
    when(clickLookup.findLatestHumanClick(anyList()))
        .thenReturn(
            Optional.of(new ClickSnapshot(7L, "messenger", "kakaotalk", "kakao.example", "kw")));

    attributor.attribute(10L, 5L, "1.2.3.4", "Mozilla/5.0");

    verify(registrationRepository)
        .recordSource(
            5L,
            7L,
            "messenger",
            "kakaotalk",
            "kakao.example",
            "kw",
            VisitorHasher.hash(7L, "1.2.3.4", "Mozilla/5.0"));
  }

  @Test
  void noMatchingClick_recordsNothing() {
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(List.of(new EventLinkEntity(10L, 7L, "기본")));
    when(clickLookup.findLatestHumanClick(anyList())).thenReturn(Optional.empty());

    attributor.attribute(10L, 5L, "1.2.3.4", "Mozilla/5.0");

    verifyNoInteractions(registrationRepository);
  }

  @Test
  void lookupFailure_recordsNothing() {
    when(eventLinkRepository.findAllByEventId(10L)).thenThrow(new RuntimeException("db down"));

    attributor.attribute(10L, 5L, "1.2.3.4", "Mozilla/5.0");

    verifyNoInteractions(registrationRepository);
  }

  @Test
  void aFailedSourceWriteIsNotTheCallersProblem() {
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(List.of(new EventLinkEntity(10L, 7L, "기본")));
    when(clickLookup.findLatestHumanClick(anyList()))
        .thenReturn(Optional.of(new ClickSnapshot(7L, null, null, null, null)));
    when(registrationRepository.recordSource(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("db down"));

    assertThatCode(() -> attributor.attribute(10L, 5L, "1.2.3.4", "Mozilla/5.0"))
        .doesNotThrowAnyException();
  }
}
