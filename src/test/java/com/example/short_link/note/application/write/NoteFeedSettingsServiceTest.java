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
import java.util.List;
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
  void eitherSettingChangesAloneAndTheAnswerCarriesBoth() {
    when(settings.read(7L))
        .thenReturn(
            new NoteFeedSettingsRepository.Preferences(false, List.of()),
            new NoteFeedSettingsRepository.Preferences(false, List.of("ko", "ja")));

    assertThat(service().update(7L, false, null))
        .isEqualTo(new NoteFeedSettingsService.FeedPreferences(false, List.of()));
    assertThat(service().update(7L, null, List.of(" KO", "ja", "ko", "")).languages())
        .containsExactly("ko", "ja");

    verify(settings).setShowsReposts(7L, false);
    verify(settings).setLanguages(7L, List.of("ko", "ja"));
  }

  @Test
  void aLanguageIsAnIsoCode() {
    assertThatThrownBy(() -> service().update(7L, null, List.of("korean!")))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LANGUAGE_INVALID));
    verify(settings, org.mockito.Mockito.never())
        .setLanguages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
