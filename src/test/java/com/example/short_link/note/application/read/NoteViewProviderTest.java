package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteFeedReader;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteViewProviderTest {

  @Mock private NoteRepository notes;
  @Mock private NoteFeedReader feeds;
  @Mock private NotePageReader pages;
  @Mock private NoteViews views;

  @Test
  void aNoteIsRenderedAsTheFeedRendersItForThatViewer() {
    NoteEntity note = mock(NoteEntity.class);
    NoteView view = mock(NoteView.class);
    when(notes.findUnblocked(9L, 7L)).thenReturn(Optional.of(note));
    when(views.ofFeed(List.of(note), 7L)).thenReturn(List.of(view));

    assertThat(new NoteViewProvider(notes, feeds, pages, views).view(9L, 7L)).contains(view);
  }

  @Test
  void aNoteTheViewerMayNotReadOrThatIsGoneIsEmpty() {
    NoteEntity hidden = mock(NoteEntity.class);
    when(notes.findUnblocked(9L, 7L)).thenReturn(Optional.of(hidden));
    when(views.ofFeed(List.of(hidden), 7L)).thenReturn(List.of());
    when(notes.findUnblocked(10L, 7L)).thenReturn(Optional.empty());

    NoteViewProvider provider = new NoteViewProvider(notes, feeds, pages, views);
    assertThat(provider.view(9L, 7L)).isEmpty();
    assertThat(provider.view(10L, 7L)).isEmpty();
  }

  @Test
  void nothingIsRenderedForAMissingNote() {
    when(notes.findUnblocked(11L, null)).thenReturn(Optional.empty());

    assertThat(new NoteViewProvider(notes, feeds, pages, views).view(11L, null)).isEmpty();
    verifyNoInteractions(views);
  }
}
