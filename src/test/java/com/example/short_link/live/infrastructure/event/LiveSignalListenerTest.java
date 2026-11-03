package com.example.short_link.live.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.live.application.LiveBus;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveSignalListenerTest {

  private final LiveBus bus = mock(LiveBus.class);
  private final LiveSignalListener listener = new LiveSignalListener(bus);

  @Test
  void changesAndRevocationsGoStraightToTheBus() {
    LiveChange change = new LiveChange(LiveTopic.room(4L), LiveChange.Kind.NOTE, 9L, 4L);
    LiveRevoke revoke = new LiveRevoke.LeftRoom(4L, 2L);

    listener.onChange(change);
    listener.onRevoke(revoke);

    verify(bus).publish(change);
    verify(bus).publish(revoke);
  }

  @Test
  void aDeletedNoteEndsTheStreamsOnItsReplies() {
    listener.onNoteDeleted(new NoteDeletedEvent(30L, 7L, List.of()));

    verify(bus).publish(new LiveRevoke.Closed(LiveTopic.note(30L)));
  }

  @Test
  void aDeletedAccountEndsItsStreamsAndThoseOnItsWriting() {
    listener.onAccountDeleted(new AccountDeletedEvent(7L));

    verify(bus).publish(new LiveRevoke.AccountGone(7L));
  }
}
