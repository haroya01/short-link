package com.example.short_link.federation.infrastructure.event;

import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.federation.application.FederationFollowers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLockListenerTest {

  @Mock private FederationFollowers followers;

  @Test
  void unlockingAcceptsTheFollowersWaitingElsewhere() {
    new AccountLockListener(followers).onUnlocked(new AccountUnlockedEvent(7L));

    verify(followers).acceptAll(7L);
  }
}
