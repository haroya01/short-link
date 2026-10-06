package com.example.short_link.note.application.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.common.link.LinkPreviewReader.Preview;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class NoteLinkPreviewServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00.123456789Z");
  private final LinkPreviewReader reader = mock(LinkPreviewReader.class);
  private final NoteLinkPreviewRepository previews = mock(NoteLinkPreviewRepository.class);
  private final NoteLinkPreviewService service =
      new NoteLinkPreviewService(reader, previews, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aCardIsStoredStampedToMicroseconds() {
    Preview card = new Preview("https://a.example", "Title", null, "https://a.example/i.png");
    when(reader.read("https://a.example")).thenReturn(Optional.of(card));

    service.refresh(1L, "https://a.example");

    verify(previews).put(1L, card, Instant.parse("2026-10-06T00:00:00.123456Z"));
  }

  @Test
  void anAddressWithNothingToShowOrNoAddressClearsTheCard() {
    when(reader.read("https://bare.example")).thenReturn(Optional.empty());
    service.refresh(1L, "https://bare.example");
    service.refresh(2L, null);

    verify(previews).remove(1L);
    verify(previews).remove(2L);
    verify(previews, never()).put(any(), any(), any());
  }

  @Test
  void aNoteDeletedBeforeTheCardLandsIsSkipped() {
    Preview card = new Preview("https://a.example", "Title", null, null);
    when(reader.read("https://a.example")).thenReturn(Optional.of(card));
    doThrow(new DataIntegrityViolationException("fk")).when(previews).put(any(), any(), any());

    service.refresh(1L, "https://a.example");

    verify(previews, never()).remove(any());
  }

  @Test
  void noAddressNeverReachesTheNetwork() {
    service.refresh(3L, null);
    verifyNoInteractions(reader);
  }
}
