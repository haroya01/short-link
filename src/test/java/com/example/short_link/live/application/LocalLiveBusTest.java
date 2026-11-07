package com.example.short_link.live.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import org.junit.jupiter.api.Test;

class LocalLiveBusTest {

  private final LiveStreams streams = mock(LiveStreams.class);
  private final LocalLiveBus bus = new LocalLiveBus(streams);

  @Test
  void aChangeIsOfferedAndARevokeEndsStreamsOnThisServer() {
    LiveChange change = new LiveChange(LiveTopic.note(1L), LiveChange.Kind.REPLY, 2L, null);
    LiveRevoke revoke = new LiveRevoke.Closed(LiveTopic.note(1L));

    bus.publish(change);
    bus.publish(revoke);

    verify(streams).offer(change);
    verify(streams).revoke(revoke);
    verifyNoMoreInteractions(streams);
  }
}
