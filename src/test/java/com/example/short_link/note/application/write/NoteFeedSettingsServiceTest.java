package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.repository.NoteFeedSettingsRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteFeedSettingsServiceTest {

  @Mock private NoteFeedSettingsRepository settings;
  @Mock private NotePeopleReader people;

  private NoteFeedSettingsService service() {
    return new NoteFeedSettingsService(settings, people);
  }

  @Test
  void hidingAndShowingResolveTheUsernameFirst() {
    when(people.activeByUsername("loud")).thenReturn(Optional.of(new NoteAuthor(9L, "loud", null)));

    assertThat(service().setRepostsHidden(7L, "loud", true).hidden()).isTrue();
    assertThat(service().setRepostsHidden(7L, "loud", false).hidden()).isFalse();

    verify(settings).hideRepostsOf(7L, 9L);
    verify(settings).showRepostsOf(7L, 9L);
  }

  @Test
  void anUnknownUsernameIsNotFound() {
    when(people.activeByUsername("gone")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().setRepostsHidden(7L, "gone", true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_NOT_FOUND));
    verifyNoInteractions(settings);
  }

  @Test
  void theGlobalSwitchIsStoredAsGiven() {
    assertThat(service().setShowReposts(7L, false).showReposts()).isFalse();

    verify(settings).setShowsReposts(7L, false);
  }
}
