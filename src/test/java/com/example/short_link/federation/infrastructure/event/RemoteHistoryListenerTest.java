package com.example.short_link.federation.infrastructure.event;

import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.RemoteAccountFollowedEvent;
import com.example.short_link.federation.application.OutboxBackfill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteHistoryListenerTest {

  @Mock private OutboxBackfill backfill;

  @Test
  void followingAnAccountFillsInItsHistoryOnce() {
    new RemoteHistoryListener(backfill).onFollowed(new RemoteAccountFollowedEvent(7L, 42L));

    verify(backfill).followed(42L);
  }
}
