package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteListEntity;
import com.example.short_link.note.domain.NoteListSummary;
import com.example.short_link.note.domain.repository.NoteListRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteListServiceTest {

  @Mock private NoteListRepository lists;
  @Mock private NotePeopleReader people;
  @Mock private NoteViews views;

  private NoteListService service() {
    return new NoteListService(lists, people, views);
  }

  @Test
  void aTitleIsTrimmedAndBoundedAndAnOwnerHasAtMostFiftyLists() {
    when(lists.countLists(7L)).thenReturn(3L);
    when(lists.save(any()))
        .thenAnswer(
            inv -> {
              NoteListEntity list = inv.getArgument(0);
              ReflectionTestUtils.setField(list, "id", 4L);
              return list;
            });
    assertThat(service().create(7L, "  디자인  ").title()).isEqualTo("디자인");

    assertThatThrownBy(() -> service().create(7L, "   "))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_TITLE_INVALID));
    when(lists.countLists(7L)).thenReturn(50L);
    assertThatThrownBy(() -> service().create(7L, "하나 더"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_LIMIT));
  }

  @Test
  void onlyTheOwnerTouchesAListAndNoOneAddsThemselves() {
    when(lists.owned(4L, 8L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service().add(8L, 4L, "mina"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_NOT_FOUND));

    when(lists.owned(4L, 7L)).thenReturn(Optional.of(new NoteListEntity(7L, "x")));
    when(people.activeByUsername("me")).thenReturn(Optional.of(new NoteAuthor(7L, "me", null)));
    assertThatThrownBy(() -> service().add(7L, 4L, "me"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_SELF));
    verify(lists, never()).addMember(any(), any());

    when(people.activeByUsername("mina")).thenReturn(Optional.of(new NoteAuthor(9L, "mina", null)));
    when(lists.countMembers(4L)).thenReturn(500L);
    assertThatThrownBy(() -> service().add(7L, 4L, "mina"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_MEMBER_LIMIT));

    when(lists.countMembers(4L)).thenReturn(499L);
    service().add(7L, 4L, "mina");
    verify(lists).addMember(4L, 9L);
    service().remove(7L, 4L, "mina");
    verify(lists).removeMember(4L, 9L);

    when(people.activeByUsername("gone")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service().add(7L, 4L, "gone"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_NOT_FOUND));
  }

  @Test
  void renamingKeepsTheCountAndDeletingRemovesTheOwnersList() {
    NoteListEntity list = new NoteListEntity(7L, "옛 이름");
    ReflectionTestUtils.setField(list, "id", 4L);
    when(lists.owned(4L, 7L)).thenReturn(Optional.of(list));
    when(lists.countMembers(4L)).thenReturn(2L);

    assertThat(service().rename(7L, 4L, " 새 이름 "))
        .isEqualTo(new NoteListService.ListView(4L, "새 이름", 2L));
    assertThatThrownBy(() -> service().rename(7L, 4L, null))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_TITLE_INVALID));
    assertThatThrownBy(() -> service().rename(7L, 4L, "가".repeat(51)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_LIST_TITLE_INVALID));

    service().delete(7L, 4L);
    verify(lists).delete(list);
  }

  @Test
  void myListsMembersAndMembershipReadOnlyWhatStillExists() {
    when(lists.summaries(7L)).thenReturn(List.of(new NoteListSummary(4L, "동료", 2L)));
    assertThat(service().mine(7L)).containsExactly(new NoteListService.ListView(4L, "동료", 2L));

    when(lists.owned(4L, 7L)).thenReturn(Optional.of(new NoteListEntity(7L, "동료")));
    when(lists.memberIds(4L)).thenReturn(List.of(9L, 10L));
    NoteAuthor mina = new NoteAuthor(9L, "mina", null);
    when(people.activeAuthors(List.of(9L, 10L))).thenReturn(Map.of(9L, mina));
    assertThat(service().members(7L, 4L)).containsExactly(mina);

    when(people.activeByUsername("mina")).thenReturn(Optional.of(mina));
    when(lists.listsContaining(7L, 9L)).thenReturn(List.of(4L));
    assertThat(service().membership(7L, "mina").listIds()).containsExactly(4L);
  }

  @Test
  void theListFeedPagesLikeEveryOtherFeed() {
    when(lists.owned(4L, 7L)).thenReturn(Optional.of(new NoteListEntity(7L, "동료")));
    NoteEntity first = mock(NoteEntity.class);
    NoteEntity second = mock(NoteEntity.class);
    when(lists.feed(4L, 7L, 0, 2)).thenReturn(List.of(first, second));
    when(views.ofFeed(List.of(first), 7L)).thenReturn(List.of());

    NoteFeedView page = service().feed(7L, 4L, -1, 1);

    assertThat(page.page()).isZero();
    assertThat(page.hasNext()).isTrue();
    verify(views).ofFeed(List.of(first), 7L);

    when(lists.feed(4L, 7L, 50, 51)).thenReturn(List.of(first));
    assertThat(service().feed(7L, 4L, 1, 500).hasNext()).isFalse();
  }
}
