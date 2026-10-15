package com.example.short_link.federation.infrastructure.event;

import com.example.short_link.common.event.RemoteAccountFollowedEvent;
import com.example.short_link.federation.application.OutboxBackfill;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RemoteHistoryListener {

  private final OutboxBackfill backfill;

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowed(RemoteAccountFollowedEvent event) {
    backfill.followed(event.remoteActorId());
  }
}
