package com.example.short_link.note.infrastructure.event;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.note.application.write.NoteScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NoteScheduleAccountListener {

  private final NoteScheduleService schedules;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountDeleted(AccountDeletedEvent event) {
    schedules.forget(event.userId());
  }
}
