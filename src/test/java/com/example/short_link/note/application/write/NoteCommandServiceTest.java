package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteCommandServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00.123456789Z");
  private static final NoteAuthor WRITER = new NoteAuthor(7L, "writer", null);

  @Mock private NoteRepository notes;
  @Mock private NoteLikeRepository likes;
  @Mock private NoteMediaRepository media;
  @Mock private QuotedPostReader quotedPosts;
  @Mock private NotePeopleReader people;
  @Mock private NoteImages images;
  @Mock private NoteViews views;
  @Mock private UserModerationGuard moderation;
  @Mock private UserBlockChecker blocks;
  @Mock private CollectionConnectionCleaner connections;
  @Mock private ApplicationEventPublisher events;

  private NoteCommandService service() {
    return new NoteCommandService(
        notes,
        likes,
        media,
        quotedPosts,
        people,
        images,
        views,
        moderation,
        blocks,
        connections,
        events,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static NoteEntity note(Long id, Long userId, String body) {
    NoteEntity note = new NoteEntity(userId, body, null, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  private void saving() {
    when(notes.save(any()))
        .thenAnswer(
            inv -> {
              NoteEntity note = inv.getArgument(0);
              ReflectionTestUtils.setField(note, "id", 100L);
              return note;
            });
  }

  @Test
  void aNoteWithImagesAndAQuoteIsStoredInOrderAndAnnounced() {
    saving();
    QuotedPost quoted = new QuotedPost(5L, "Essay", "essay", "writer");
    when(quotedPosts.publishedByIds(Set.of(5L))).thenReturn(Map.of(5L, quoted));
    when(images.verify(7L, new NoteDraft.Image("k1", "first")))
        .thenReturn(new NoteImages.StoredImage("k1", "https://cdn/k1", "image/png", "first"));
    when(images.verify(7L, new NoteDraft.Image("k2", null)))
        .thenReturn(new NoteImages.StoredImage("k2", "https://cdn/k2", "image/jpeg", null));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));

    NoteView view =
        service()
            .create(
                7L,
                new NoteDraft(
                    "  hello  ",
                    List.of(new NoteDraft.Image("k1", "first"), new NoteDraft.Image("k2", null)),
                    5L,
                    null));

    verify(moderation).requireCanWrite(7L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<NoteMediaEntity>> rows = ArgumentCaptor.forClass(List.class);
    verify(media).saveAll(rows.capture());
    assertThat(rows.getValue())
        .extracting(NoteMediaEntity::getPosition, NoteMediaEntity::getStorageKey)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(0, "k1"),
            org.assertj.core.groups.Tuple.tuple(1, "k2"));
    verify(events).publishEvent(new NotePublishedEvent(100L, 7L));
    assertThat(view.body()).isEqualTo("hello");
    assertThat(view.quotedPost()).isEqualTo(quoted);
    assertThat(view.author()).isEqualTo(WRITER);
    assertThat(view.likeCount()).isZero();
    assertThat(view.media())
        .extracting(NoteView.Media::url)
        .containsExactly("https://cdn/k1", "https://cdn/k2");
  }

  @Test
  void anImageAloneIsEnoughButNothingAtAllIsNot() {
    saving();
    when(images.verify(any(), any()))
        .thenReturn(new NoteImages.StoredImage("k", "u", "image/png", null));
    service().create(7L, new NoteDraft(null, List.of(new NoteDraft.Image("k", null)), null, null));

    assertThatThrownBy(() -> service().create(7L, new NoteDraft("   ", null, null, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_BODY_REQUIRED));
  }

  @Test
  void bodyLengthCountsCharactersNotUtf16Units() {
    saving();
    String fiveHundredEmoji = "😀".repeat(NoteEntity.MAX_BODY_LENGTH);
    service().create(7L, new NoteDraft(fiveHundredEmoji, null, null, null));

    assertThatThrownBy(
            () -> service().create(7L, new NoteDraft(fiveHundredEmoji + "x", null, null, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_BODY_TOO_LONG));
  }

  @Test
  void atMostFourImages() {
    List<NoteDraft.Image> five =
        List.of(
            new NoteDraft.Image("a", null),
            new NoteDraft.Image("b", null),
            new NoteDraft.Image("c", null),
            new NoteDraft.Image("d", null),
            new NoteDraft.Image("e", null));

    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", five, null, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_TOO_MANY_IMAGES));
    verify(notes, never()).save(any());
  }

  @Test
  void aQuoteMustBeAPublishedPost() {
    when(quotedPosts.publishedByIds(Set.of(9L))).thenReturn(Map.of());

    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", null, 9L, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_QUOTE_NOT_FOUND));
  }

  @Test
  void repliesFollowTheParentAndRespectBlocksBothWays() {
    saving();
    when(notes.findById(50L)).thenReturn(Optional.of(note(50L, 8L, "parent")));
    NoteView reply = service().create(7L, new NoteDraft("re", null, null, 50L));
    assertThat(reply.inReplyToId()).isEqualTo(50L);

    when(blocks.isBlocked(8L, 7L)).thenReturn(true);
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("re", null, null, 50L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_REPLY_BLOCKED));
    when(blocks.isBlocked(8L, 7L)).thenReturn(false);
    when(blocks.isBlocked(7L, 8L)).thenReturn(true);
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("re", null, null, 50L)))
        .isInstanceOf(NoteException.class);

    when(notes.findById(51L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("re", null, null, 51L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_NOT_FOUND));
  }

  @Test
  void onlyTheAuthorEditsAndTheEditIsStampedToMicroseconds() {
    NoteEntity mine = note(1L, 7L, "old");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    NoteView edited =
        new NoteView(1L, "new", null, null, 0L, false, WRITER, List.of(), null, null, 0);
    when(views.of(List.of(mine), 7L)).thenReturn(List.of(edited));

    assertThat(service().edit(7L, 1L, " new ")).isEqualTo(edited);
    assertThat(mine.getBody()).isEqualTo("new");
    assertThat(mine.getEditedAt()).isEqualTo(Instant.parse("2026-10-06T00:00:00.123456Z"));
    verify(events).publishEvent(new NoteEditedEvent(1L, 7L));

    assertThatThrownBy(() -> service().edit(8L, 1L, "theirs"))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_PERMISSION_DENIED));
  }

  @Test
  void anImageNoteMayBeEditedToNoText() {
    NoteEntity mine = note(1L, 7L, "caption");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L)))
        .thenReturn(List.of(new NoteMediaEntity(1L, 0, "k", "u", "image/png", null)));
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));

    service().edit(7L, 1L, "");

    assertThat(mine.getBody()).isEmpty();
  }

  @Test
  void deletingClearsLikesAndCollectionBlocksAndHandsOverImageKeys() {
    NoteEntity mine = note(1L, 7L, "bye");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L)))
        .thenReturn(List.of(new NoteMediaEntity(1L, 0, "k", "u", "image/png", null)));

    service().delete(7L, 1L);

    verify(likes).deleteAllByNoteId(1L);
    verify(connections).purgeForNote(1L);
    verify(notes).delete(mine);
    verify(events).publishEvent(new NoteDeletedEvent(1L, 7L, List.of("k")));
  }

  @Test
  void likeCountsAreReturnedOnlyToTheAuthor() {
    when(notes.findById(1L)).thenReturn(Optional.of(note(1L, 7L, "x")));
    when(likes.countByNoteId(1L)).thenReturn(3L);

    assertThat(service().setLike(7L, 1L, true))
        .isEqualTo(new NoteCommandService.LikeStatus(true, 3L));
    assertThat(service().setLike(8L, 1L, true))
        .isEqualTo(new NoteCommandService.LikeStatus(true, 0L));
    assertThat(service().setLike(8L, 1L, false))
        .isEqualTo(new NoteCommandService.LikeStatus(false, 0L));
    verify(likes).delete(1L, 8L);
  }
}
