package com.example.short_link.federation.infrastructure.event;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.federation.application.FederationFollowers;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountLockListener {

  private final FederationFollowers followers;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUnlocked(AccountUnlockedEvent event) {
    followers.acceptAll(event.userId());
  }
}
