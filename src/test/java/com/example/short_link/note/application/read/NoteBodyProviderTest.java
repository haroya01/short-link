package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteBodyProviderTest {

  @Mock private NoteRepository notes;
  @Mock private NotePeopleReader people;
  @InjectMocks private NoteBodyProvider provider;

  @Test
  void collectionsSeeBlocksWithTheirAuthorAndExistence() {
    NoteEntity note = note(3L, 7L, "body");
    when(notes.findAllByIdIn(List.of(3L, 4L))).thenReturn(List.of(note));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, new NoteAuthor(7L, "yuna", null)));
    when(notes.findById(3L)).thenReturn(Optional.of(note));
    when(notes.findById(4L)).thenReturn(Optional.empty());

    assertThat(provider.blocksByIds(List.of(3L, 4L)))
        .isEqualTo(Map.of(3L, new NoteBlock(3L, "body", "yuna")));
    assertThat(provider.exists(3L)).isTrue();
    assertThat(provider.exists(4L)).isFalse();
  }

  @Test
  void aNoteWhoseAuthorIsGoneIsLeftOut() {
    when(notes.findAllByIdIn(List.of(3L))).thenReturn(List.of(note(3L, 7L, "body")));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of());

    assertThat(provider.blocksByIds(List.of(3L))).isEmpty();
  }

  @Test
  void noNotesSkipsTheAuthorLookup() {
    when(notes.findAllByIdIn(List.of())).thenReturn(List.of());

    assertThat(provider.blocksByIds(List.of())).isEmpty();
    verify(people, never()).activeAuthors(any());
  }

  private static NoteEntity note(Long id, Long userId, String body) {
    NoteEntity note = new NoteEntity(userId, body, null, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }
}
