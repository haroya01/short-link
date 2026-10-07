package com.example.short_link.note.application.moderation;

import com.example.short_link.common.note.NoteModerationPort;
import com.example.short_link.note.application.write.NoteCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NoteModerationAdapter implements NoteModerationPort {

  private final NoteCommandService notes;

  @Override
  public void takeDown(Long adminUserId, Long noteId) {
    log.info("admin note take-down: adminUserId={}, noteId={}", adminUserId, noteId);
    notes.takeDown(noteId);
  }
}
