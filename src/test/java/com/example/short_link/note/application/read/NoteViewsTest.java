package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinkPreviewEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
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
  @Mock private NoteRepostRepository reposts;
  @Mock private NoteLinkPreviewRepository linkPreviews;
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
  void everyoneSeesTheCountsAndOnlyTheirOwnHeart() {
    NoteEntity mine = note(1L, 7L, 5L);
    NoteEntity theirs = note(2L, 8L, null);
    when(people.activeAuthors(Set.of(7L, 8L)))
        .thenReturn(
            Map.of(7L, new NoteAuthor(7L, "me", null), 8L, new NoteAuthor(8L, "them", "a.png")));
    when(media.findByNoteIds(List.of(1L, 2L)))
        .thenReturn(List.of(new NoteMediaEntity(2L, 0, "k", "https://cdn/k", "image/png", "alt")));
    when(quotedPosts.publishedByIds(Set.of(5L)))
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "me")));
    when(notes.stats(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, new NoteStats(0, 5, 1), 2L, new NoteStats(4, 2, 0)));
    when(likes.likedNoteIds(7L, List.of(1L, 2L))).thenReturn(List.of(2L));

    List<NoteView> page = views.of(List.of(mine, theirs), 7L);

    assertThat(page.get(0).likeCount()).isEqualTo(5L);
    assertThat(page.get(0).repostCount()).isEqualTo(1L);
    assertThat(page.get(0).likedByMe()).isFalse();
    assertThat(page.get(0).quotedPost().slug()).isEqualTo("essay");
    assertThat(page.get(1).likeCount()).isEqualTo(2L);
    assertThat(page.get(1).likedByMe()).isTrue();
    assertThat(page.get(1).replyCount()).isEqualTo(4L);
    assertThat(page.get(1).media())
        .containsExactly(new NoteView.Media("https://cdn/k", "alt", "image/png"));
  }

  @Test
  void aQuotedNoteIsBatchedWithItsAuthorAndImagesAndRepostCountsArePublic() {
    NoteEntity quoting = note(1L, 7L, null);
    ReflectionTestUtils.setField(quoting, "quotedNoteId", 50L);
    NoteEntity gone = note(2L, 7L, null);
    ReflectionTestUtils.setField(gone, "quotedNoteId", 60L);
    when(notes.findAllByIdIn(Set.of(50L, 60L)))
        .thenReturn(List.of(note(50L, 8L, null), note(60L, 9L, null)));
    NoteAuthor them = new NoteAuthor(8L, "them", null);
    when(people.activeAuthors(Set.of(7L, 8L, 9L)))
        .thenReturn(Map.of(7L, new NoteAuthor(7L, "me", null), 8L, them));
    when(media.findByNoteIds(List.of(1L, 2L, 50L)))
        .thenReturn(List.of(new NoteMediaEntity(50L, 0, "k", "https://cdn/k", "image/png", "alt")));
    when(notes.stats(List.of(1L, 2L))).thenReturn(Map.of(1L, new NoteStats(0, 0, 2)));
    when(reposts.repostedNoteIds(7L, List.of(1L, 2L))).thenReturn(List.of());
    when(reposts.repostedNoteIds(8L, List.of(1L, 2L))).thenReturn(List.of(2L));

    List<NoteView> asAuthor = views.of(List.of(quoting, gone), 7L);
    assertThat(asAuthor.get(0).quotedNote().author()).isEqualTo(them);
    assertThat(asAuthor.get(0).quotedNote().body()).isEqualTo("n50");
    assertThat(asAuthor.get(0).quotedNote().media())
        .containsExactly(new NoteView.Media("https://cdn/k", "alt", "image/png"));
    assertThat(asAuthor.get(0).media()).isEmpty();
    assertThat(asAuthor.get(0).repostCount()).isEqualTo(2L);
    assertThat(asAuthor.get(1).quotedNote()).isNull();
    assertThat(asAuthor.get(1).repostCount()).isZero();

    List<NoteView> asOther = views.of(List.of(quoting, gone), 8L);
    assertThat(asOther.get(0).repostCount()).isEqualTo(2L);
    assertThat(asOther.get(0).repostedByMe()).isFalse();
    assertThat(asOther.get(1).repostedByMe()).isTrue();
  }

  @Test
  void onlyNotesWithAnAddressAreLookedUpForACard() {
    NoteEntity linked = new NoteEntity(8L, "see https://a.example", null, null);
    ReflectionTestUtils.setField(linked, "id", 1L);
    NoteEntity plain = note(2L, 8L, null);
    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of(8L, new NoteAuthor(8L, "them", null)));
    NoteLinkPreviewEntity card = org.mockito.Mockito.mock(NoteLinkPreviewEntity.class);
    when(card.getNoteId()).thenReturn(1L);
    when(card.getUrl()).thenReturn("https://a.example");
    when(card.getTitle()).thenReturn("A");
    when(card.getImageUrl()).thenReturn("https://a.example/i.png");
    when(linkPreviews.findByNoteIds(List.of(1L))).thenReturn(List.of(card));

    List<NoteView> page = views.of(List.of(linked, plain), null);

    assertThat(page.get(0).linkPreview())
        .isEqualTo(
            new NoteView.LinkPreview("https://a.example", "A", null, "https://a.example/i.png"));
    assertThat(page.get(1).linkPreview()).isNull();

    views.of(List.of(plain), null);
    verify(linkPreviews, org.mockito.Mockito.times(1)).findByNoteIds(anyCollection());
  }

  @Test
  void anonymousReadersGetNoViewerFields() {
    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of(8L, new NoteAuthor(8L, "them", null)));

    NoteView view = views.of(List.of(note(2L, 8L, null)), null).getFirst();

    assertThat(view.likeCount()).isZero();
    assertThat(view.likedByMe()).isNull();
    assertThat(view.repostCount()).isZero();
    assertThat(view.repostedByMe()).isNull();
    verify(likes, never()).likedNoteIds(any(), anyCollection());
    verify(quotedPosts, never()).publishedByIds(anyCollection());
  }
}
