package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.exception.NoteException;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteQueryServiceTest {

  private static final NoteAuthor ME = new NoteAuthor(7L, "me", null);

  @Mock private NoteRepository notes;
  @Mock private NoteLikeRepository likes;
  @Mock private NotePeopleReader people;
  @Mock private NoteViews views;
  @InjectMocks private NoteQueryService service;

  private static NoteEntity note(Long id, Long inReplyTo) {
    NoteEntity note = new NoteEntity(7L, "n" + id, inReplyTo, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  private static NoteView view(Long id, Long inReplyTo, Long likeCount) {
    return new NoteView(
        id, "n" + id, null, null, likeCount, null, ME, List.of(), null, inReplyTo, 0);
  }

  @Test
  void pagesFetchOneExtraRowToKnowIfMoreFollow() {
    List<NoteEntity> three = LongStream.of(3, 2, 1).mapToObj(id -> note(id, null)).toList();
    when(notes.topLevel(2, 3)).thenReturn(three);
    when(views.of(three.subList(0, 2), 7L))
        .thenReturn(List.of(view(3L, null, 4L), view(2L, null, null)));

    NoteFeedView feed = service.everyone(1, 2, 7L);

    assertThat(feed.hasNext()).isTrue();
    assertThat(feed.page()).isEqualTo(1);
    assertThat(feed.items()).extracting(NoteView::likeCount).containsExactly(4L, 0L);
  }

  @Test
  void pageAndSizeAreClamped() {
    when(notes.topLevel(0, NoteQueryService.MAX_PAGE_SIZE + 1)).thenReturn(List.of());
    when(views.of(List.of(), null)).thenReturn(List.of());

    NoteFeedView feed = service.everyone(-3, 10_000, null);

    assertThat(feed.page()).isZero();
    assertThat(feed.hasNext()).isFalse();
  }

  @Test
  void aProfileOfNoOneIs404() {
    when(people.activeByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.byAuthor("ghost", 0, 20, null))
        .isInstanceOf(NoteException.class);
  }

  @Test
  void profileAndFollowingReadTheRightAuthors() {
    when(people.activeByUsername("me")).thenReturn(Optional.of(ME));
    when(notes.topLevelByAuthors(List.of(7L), 0, 21)).thenReturn(List.of());
    when(views.of(anyList(), eq(9L))).thenReturn(List.of());
    service.byAuthor("me", 0, 20, 9L);

    when(people.followingIds(9L)).thenReturn(List.of(7L, 8L));
    when(notes.topLevelByAuthors(List.of(7L, 8L, 9L), 0, 21)).thenReturn(List.of());
    assertThat(service.following(9L, 0, 20).items()).isEmpty();
  }

  @Test
  void aThreadCarriesItsParentAndReplies() {
    NoteEntity main = note(2L, 1L);
    NoteEntity parent = note(1L, null);
    NoteEntity reply = note(3L, 2L);
    when(notes.findById(2L)).thenReturn(Optional.of(main));
    when(notes.findById(1L)).thenReturn(Optional.of(parent));
    when(notes.replies(2L, NoteQueryService.MAX_REPLIES)).thenReturn(List.of(reply));
    when(views.of(List.of(main, parent, reply), 9L))
        .thenReturn(List.of(view(2L, 1L, null), view(1L, null, null), view(3L, 2L, null)));

    NoteThreadView thread = service.thread(2L, 9L);

    assertThat(thread.note().id()).isEqualTo(2L);
    assertThat(thread.parent().id()).isEqualTo(1L);
    assertThat(thread.replies()).extracting(NoteView::id).containsExactly(3L);
  }

  @Test
  void aThreadWhoseAuthorLeftIs404AndAMissingParentIsNull() {
    NoteEntity orphan = note(2L, 1L);
    when(notes.findById(2L)).thenReturn(Optional.of(orphan));
    when(notes.findById(1L)).thenReturn(Optional.empty());
    when(notes.replies(2L, NoteQueryService.MAX_REPLIES)).thenReturn(List.of());
    when(views.of(List.of(orphan), null)).thenReturn(List.of(view(2L, 1L, null)));
    assertThat(service.thread(2L, null).parent()).isNull();

    when(views.of(List.of(orphan), 5L)).thenReturn(List.of());
    assertThatThrownBy(() -> service.thread(2L, 5L)).isInstanceOf(NoteException.class);

    when(notes.findById(4L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.thread(4L, null)).isInstanceOf(NoteException.class);
  }

  @Test
  void likedIdsPassThrough() {
    when(likes.likedNoteIds(7L, List.of(1L))).thenReturn(List.of(1L));

    assertThat(service.likedNoteIds(7L, List.of(1L))).containsExactly(1L);
  }
}
