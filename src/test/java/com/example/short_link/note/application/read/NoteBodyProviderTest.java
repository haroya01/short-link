package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteBodyProviderTest {

  @Mock private NoteRepository notes;
  @InjectMocks private NoteBodyProvider provider;

  @Test
  void collectionsSeeBodiesByIdAndExistence() {
    NoteEntity note = new NoteEntity(7L, "body", null, null);
    ReflectionTestUtils.setField(note, "id", 3L);
    when(notes.findAllByIdIn(List.of(3L, 4L))).thenReturn(List.of(note));
    when(notes.findById(3L)).thenReturn(Optional.of(note));
    when(notes.findById(4L)).thenReturn(Optional.empty());

    assertThat(provider.bodiesByIds(List.of(3L, 4L))).isEqualTo(Map.of(3L, "body"));
    assertThat(provider.exists(3L)).isTrue();
    assertThat(provider.exists(4L)).isFalse();
  }
}
