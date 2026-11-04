package com.example.short_link.live.infrastructure.event;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.live.application.LiveBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LiveSignalListener {

  private final LiveBus bus;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onChange(LiveChange change) {
    bus.publish(change);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRevoke(LiveRevoke revoke) {
    bus.publish(revoke);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteDeleted(NoteDeletedEvent event) {
    bus.publish(new LiveRevoke.Closed(LiveTopic.note(event.noteId())));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountDeleted(AccountDeletedEvent event) {
    bus.publish(new LiveRevoke.AccountGone(event.userId()));
  }
}
