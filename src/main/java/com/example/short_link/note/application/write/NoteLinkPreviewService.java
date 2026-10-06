package com.example.short_link.note.application.write;

import com.example.short_link.common.link.LinkPreviewReader;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteLinkPreviewService {

  private final LinkPreviewReader reader;
  private final NoteLinkPreviewRepository previews;
  private final Clock clock;

  public void refresh(Long noteId, String url) {
    var preview = url == null ? null : reader.read(url).orElse(null);
    if (preview == null) {
      previews.remove(noteId);
      return;
    }
    try {
      previews.put(noteId, preview, clock.instant().truncatedTo(ChronoUnit.MICROS));
    } catch (DataIntegrityViolationException deleted) {
      log.debug("note {} was deleted before its link preview landed", noteId);
    }
  }
}
