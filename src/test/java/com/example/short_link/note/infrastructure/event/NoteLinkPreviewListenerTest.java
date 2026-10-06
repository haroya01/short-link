package com.example.short_link.note.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.note.application.write.NoteLinkPreviewRequested;
import com.example.short_link.note.application.write.NoteLinkPreviewService;
import org.junit.jupiter.api.Test;

class NoteLinkPreviewListenerTest {

  @Test
  void aRequestRefreshesThatNotesCard() {
    NoteLinkPreviewService service = mock(NoteLinkPreviewService.class);

    new NoteLinkPreviewListener(service)
        .onRequested(new NoteLinkPreviewRequested(5L, "https://a.example"));

    verify(service).refresh(5L, "https://a.example");
  }
}
