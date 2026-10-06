package com.example.short_link.federation.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.federation.application.FederationLeaving;
import com.example.short_link.federation.application.NoteFederation;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteFederationListenerTest {

  private final NoteFederation notes = mock(NoteFederation.class);
  private final FederationLeaving leaving = mock(FederationLeaving.class);
  private final NoteFederationListener listener = new NoteFederationListener(notes, leaving);

  @Test
  void eachEventReachesItsHandler() {
    listener.onPublished(new NotePublishedEvent(1L, 7L));
    listener.onEdited(new NoteEditedEvent(1L, 7L));
    listener.onDeleted(new NoteDeletedEvent(1L, 7L, List.of()));
    listener.onReposted(new NoteRepostedEvent(900L, 1L, 8L));
    listener.onUnreposted(new NoteUnrepostedEvent(900L, 1L, 8L));
    listener.onAccountDeleted(new AccountDeletedEvent(7L));

    verify(notes).created(1L, 7L);
    verify(notes).edited(1L, 7L);
    verify(notes).deleted(1L, 7L);
    verify(notes).reposted(900L, 1L, 8L);
    verify(notes).unreposted(900L, 1L, 8L);
    verify(leaving).leave(7L);
  }
}
