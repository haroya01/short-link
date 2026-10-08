package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.Image;
import com.example.short_link.common.note.NoteSnapshotReader.Quote;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
  @Mock private NotePollRepository polls;
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
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "yuki", 1L)));
    when(media.findByNoteIds(List.of(42L)))
        .thenReturn(List.of(new NoteMediaEntity(42L, 0, "k", "https://cdn/k", "image/png", "alt")));

    var snapshot = provider.find(42L).orElseThrow();

    assertThat(snapshot.authorUsername()).isEqualTo("yuki");
    assertThat(snapshot.inReplyToId()).isEqualTo(3L);
    assertThat(snapshot.quote()).isEqualTo(new Quote("Essay", "essay", "yuki"));
    assertThat(snapshot.images()).containsExactly(new Image("https://cdn/k", "image/png", "alt"));
  }

  @Test
  void aQuotedNoteResolvesOnlyWhileItAndItsAuthorExist() {
    NoteEntity quoting = note(null);
    ReflectionTestUtils.setField(quoting, "quotedNoteId", 50L);
    NoteEntity quoted = new NoteEntity(8L, "original", null, null);
    ReflectionTestUtils.setField(quoted, "id", 50L);
    when(notes.findById(42L)).thenReturn(Optional.of(quoting));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, new NoteAuthor(7L, "yuki", null)));
    when(media.findByNoteIds(List.of(42L))).thenReturn(List.of());
    when(notes.findById(50L)).thenReturn(Optional.of(quoted));
    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of(8L, new NoteAuthor(8L, "mio", null)));

    assertThat(provider.find(42L).orElseThrow().quotedNote()).isEqualTo(new QuotedNote(50L, "mio"));

    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of());
    assertThat(provider.find(42L).orElseThrow().quotedNote()).isNull();

    when(notes.findById(50L)).thenReturn(Optional.empty());
    assertThat(provider.find(42L).orElseThrow().quotedNote()).isNull();
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

  @Test
  void aPollCarriesItsCountsAndWhetherItHasEnded() {
    NoteEntity note = new NoteEntity(7L, "어디서 볼까?", null, null);
    ReflectionTestUtils.setField(note, "id", 42L);
    Instant ends = Instant.parse("2026-10-07T12:00:00Z");
    note.attachPoll(List.of("강남", "홍대"), ends, false);
    NoteSnapshotProvider ended =
        new NoteSnapshotProvider(
            notes,
            media,
            people,
            quotedPosts,
            polls,
            Clock.fixed(ends.plusSeconds(1), ZoneOffset.UTC));
    when(notes.findById(42L)).thenReturn(Optional.of(note));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, new NoteAuthor(7L, "yuki", null)));
    when(polls.tallies(List.of(42L), null))
        .thenReturn(Map.of(42L, new NotePollTally(3, List.of(2L, 1L, 0L, 0L), null)));

    NoteSnapshotReader.Poll poll = ended.find(42L).orElseThrow().poll();

    assertThat(poll.options())
        .containsExactly(
            new NoteSnapshotReader.PollOption("강남", 2), new NoteSnapshotReader.PollOption("홍대", 1));
    assertThat(poll.voters()).isEqualTo(3);
    assertThat(poll.endTime()).isEqualTo(ends);
    assertThat(poll.closed()).isTrue();
  }
}
