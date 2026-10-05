package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader.Image;
import com.example.short_link.common.note.NoteSnapshotReader.Quote;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
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
class NoteSnapshotProviderTest {

  @Mock private NoteRepository notes;
  @Mock private NoteMediaRepository media;
  @Mock private NotePeopleReader people;
  @Mock private QuotedPostReader quotedPosts;
  @InjectMocks private NoteSnapshotProvider provider;

  private static NoteEntity note(Long quotedPostId) {
    NoteEntity note = new NoteEntity(7L, "hi", 3L, quotedPostId);
    ReflectionTestUtils.setField(note, "id", 42L);
    return note;
  }

  @Test
  void aNoteResolvesWithItsAuthorQuoteAndImages() {
    when(notes.findById(42L)).thenReturn(Optional.of(note(5L)));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, new NoteAuthor(7L, "yuki", null)));
    when(quotedPosts.publishedByIds(Set.of(5L)))
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "yuki")));
    when(media.findByNoteIds(List.of(42L)))
        .thenReturn(List.of(new NoteMediaEntity(42L, 0, "k", "https://cdn/k", "image/png", "alt")));

    var snapshot = provider.find(42L).orElseThrow();

    assertThat(snapshot.authorUsername()).isEqualTo("yuki");
    assertThat(snapshot.inReplyToId()).isEqualTo(3L);
    assertThat(snapshot.quote()).isEqualTo(new Quote("Essay", "essay", "yuki"));
    assertThat(snapshot.images()).containsExactly(new Image("https://cdn/k", "image/png", "alt"));
  }

  @Test
  void anUnpublishedQuoteIsDroppedAndAGoneAuthorOrNoteResolvesToNothing() {
    when(notes.findById(42L)).thenReturn(Optional.of(note(5L)));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, new NoteAuthor(7L, "yuki", null)));
    when(quotedPosts.publishedByIds(Set.of(5L))).thenReturn(Map.of());
    when(media.findByNoteIds(List.of(42L))).thenReturn(List.of());
    assertThat(provider.find(42L).orElseThrow().quote()).isNull();

    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of());
    assertThat(provider.find(42L)).isEmpty();

    when(notes.findById(1L)).thenReturn(Optional.empty());
    assertThat(provider.find(1L)).isEmpty();

    when(notes.countByAuthor(7L)).thenReturn(2L);
    assertThat(provider.countByAuthor(7L)).isEqualTo(2L);
  }
}
