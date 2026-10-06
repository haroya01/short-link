package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteViewsTest {

  @Mock private NoteRepository notes;
  @Mock private NoteLikeRepository likes;
  @Mock private NoteMediaRepository media;
  @Mock private NotePeopleReader people;
  @Mock private QuotedPostReader quotedPosts;
  @InjectMocks private NoteViews views;

  private static NoteEntity note(Long id, Long userId, Long quotedPostId) {
    NoteEntity note = new NoteEntity(userId, "n" + id, null, quotedPostId);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  @Test
  void anEmptyPageQueriesNothing() {
    assertThat(views.of(List.of(), 7L)).isEmpty();
    verifyNoInteractions(people, media, likes, notes, quotedPosts);
  }

  @Test
  void notesByDeletedAuthorsDisappear() {
    when(people.activeAuthors(Set.of(9L))).thenReturn(Map.of());

    assertThat(views.of(List.of(note(1L, 9L, null)), null)).isEmpty();
    verifyNoInteractions(media, likes, quotedPosts);
  }

  @Test
  void theAuthorSeesTheirLikeCountOthersSeeOnlyTheirOwnHeart() {
    NoteEntity mine = note(1L, 7L, 5L);
    NoteEntity theirs = note(2L, 8L, null);
    when(people.activeAuthors(Set.of(7L, 8L)))
        .thenReturn(
            Map.of(7L, new NoteAuthor(7L, "me", null), 8L, new NoteAuthor(8L, "them", "a.png")));
    when(media.findByNoteIds(List.of(1L, 2L)))
        .thenReturn(List.of(new NoteMediaEntity(2L, 0, "k", "https://cdn/k", "image/png", "alt")));
    when(quotedPosts.publishedByIds(Set.of(5L)))
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "me")));
    when(notes.replyCounts(List.of(1L, 2L))).thenReturn(Map.of(2L, 4L));
    when(likes.counts(List.of(1L))).thenReturn(Map.of(1L, 3L));
    when(likes.likedNoteIds(7L, List.of(1L, 2L))).thenReturn(List.of(2L));

    List<NoteView> page = views.of(List.of(mine, theirs), 7L);

    assertThat(page.get(0).likeCount()).isEqualTo(3L);
    assertThat(page.get(0).likedByMe()).isFalse();
    assertThat(page.get(0).quotedPost().slug()).isEqualTo("essay");
    assertThat(page.get(1).likeCount()).isNull();
    assertThat(page.get(1).likedByMe()).isTrue();
    assertThat(page.get(1).replyCount()).isEqualTo(4L);
    assertThat(page.get(1).media())
        .containsExactly(new NoteView.Media("https://cdn/k", "alt", "image/png"));
  }

  @Test
  void anonymousReadersGetNoViewerFields() {
    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of(8L, new NoteAuthor(8L, "them", null)));

    NoteView view = views.of(List.of(note(2L, 8L, null)), null).getFirst();

    assertThat(view.likeCount()).isNull();
    assertThat(view.likedByMe()).isNull();
    verify(likes, never()).counts(anyCollection());
    verify(quotedPosts, never()).publishedByIds(anyCollection());
  }
}
