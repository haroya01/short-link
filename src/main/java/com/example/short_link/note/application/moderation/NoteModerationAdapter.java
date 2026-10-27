package com.example.short_link.note.application.moderation;

import com.example.short_link.common.note.NoteModerationPort;
import com.example.short_link.note.application.write.NoteThreadControls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NoteModerationAdapter implements NoteModerationPort {

  private final NoteThreadControls threads;

  @Override
  public void takeDown(Long adminUserId, Long noteId) {
    log.info("admin note take-down: adminUserId={}, noteId={}", adminUserId, noteId);
    threads.takeDown(noteId);
  }
}
