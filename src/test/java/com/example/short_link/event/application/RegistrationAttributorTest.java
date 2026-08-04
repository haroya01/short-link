package com.example.short_link.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.RegistrationClickLookup.ClickSnapshot;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
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

  private RegistrationAttributor attributor;

  @BeforeEach
  void setUp() {
    attributor = new RegistrationAttributor(eventLinkRepository, clickLookup);
  }

  private EventRegistrationEntity registration() {
    return new EventRegistrationEntity(10L, "이름", "a@x.com", null, "hash");
  }

  @Test
  void eventWithoutLinks_skipsLookup() {
    when(eventLinkRepository.findAllByEventId(10L)).thenReturn(List.of());
    EventRegistrationEntity registration = registration();

    attributor.attribute(10L, registration, "1.2.3.4", "Mozilla/5.0");

    verify(clickLookup, never()).findLatestHumanClick(anyList());
    assertThat(registration.getLinkId()).isNull();
  }

  @Test
  void matchedClick_stampsChannelSnapshotAndVisitorHash() {
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(List.of(new EventLinkEntity(10L, 7L, "기본")));
    when(clickLookup.findLatestHumanClick(anyList()))
        .thenReturn(
            Optional.of(new ClickSnapshot(7L, "messenger", "kakaotalk", "kakao.example", "kw")));
    EventRegistrationEntity registration = registration();

    attributor.attribute(10L, registration, "1.2.3.4", "Mozilla/5.0");

    assertThat(registration.getLinkId()).isEqualTo(7L);
    assertThat(registration.getSourceChannel()).isEqualTo("messenger");
    assertThat(registration.getClientApp()).isEqualTo("kakaotalk");
    assertThat(registration.getReferrerHost()).isEqualTo("kakao.example");
    assertThat(registration.getUtmSource()).isEqualTo("kw");
    assertThat(registration.getVisitorHash())
        .isEqualTo(VisitorHasher.hash(7L, "1.2.3.4", "Mozilla/5.0"));
  }

  @Test
  void lookupFailure_leavesRegistrationUnattributed() {
    when(eventLinkRepository.findAllByEventId(10L)).thenThrow(new RuntimeException("db down"));
    EventRegistrationEntity registration = registration();

    attributor.attribute(10L, registration, "1.2.3.4", "Mozilla/5.0");

    assertThat(registration.getLinkId()).isNull();
  }
}
