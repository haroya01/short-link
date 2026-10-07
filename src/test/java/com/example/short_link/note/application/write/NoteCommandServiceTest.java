package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.common.event.RemoteNoteLikedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteRepostEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
  @Mock private NoteRepostRepository reposts;
  @Mock private NoteBookmarkRepository bookmarks;
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
        reposts,
        bookmarks,
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
    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.REPLY, 8L, 7L, null, 50L, "parent", 100L, "re", 50L));

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
  void followersWhoAskedHearOfNewNotesAndSelfThreadsButNotRepliesToOthersOrDirectNotes() {
    saving();
    when(notes.findById(50L)).thenReturn(Optional.of(note(50L, 8L, "parent")));
    when(notes.findById(60L)).thenReturn(Optional.of(note(60L, 7L, "first")));

    service().create(7L, new NoteDraft("hello", null, null, null));
    service().create(7L, new NoteDraft("re", null, null, 50L));
    service().create(7L, new NoteDraft("and then", null, null, 60L));

    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(published.capture());
    assertThat(published.getAllValues())
        .filteredOn(NoteBroadcastEvent.class::isInstance)
        .containsExactly(
            new NoteBroadcastEvent(100L, 7L, "hello"),
            new NoteBroadcastEvent(100L, 7L, "and then"));
  }

  @Test
  void aDirectNoteIsNeverBroadcast() {
    saving();
    service()
        .create(7L, new NoteDraft("psst", null, null, null, null, null, false, "DIRECT", null));

    verify(events, never()).publishEvent(any(NoteBroadcastEvent.class));
  }

  @Test
  void aQuotedNoteIsStoredAndComesBackWithItsAuthorAndImages() {
    saving();
    NoteAuthor other = new NoteAuthor(8L, "other", null);
    when(notes.findById(50L)).thenReturn(Optional.of(note(50L, 8L, "original")));
    when(people.activeAuthors(Set.of(7L, 8L))).thenReturn(Map.of(7L, WRITER, 8L, other));
    when(media.findByNoteIds(List.of(50L)))
        .thenReturn(List.of(new NoteMediaEntity(50L, 0, "k", "https://cdn/k", "image/png", "alt")));

    NoteView view = service().create(7L, new NoteDraft("so true", null, null, null, 50L));

    ArgumentCaptor<NoteEntity> saved = ArgumentCaptor.forClass(NoteEntity.class);
    verify(notes).save(saved.capture());
    assertThat(saved.getValue().getQuotedNoteId()).isEqualTo(50L);
    assertThat(view.quotedNote().id()).isEqualTo(50L);
    assertThat(view.quotedNote().body()).isEqualTo("original");
    assertThat(view.quotedNote().author()).isEqualTo(other);
    assertThat(view.quotedNote().media())
        .containsExactly(new NoteView.Media("https://cdn/k", "alt", "image/png"));
    assertThat(view.repostCount()).isZero();
    assertThat(view.repostedByMe()).isFalse();
    verify(events).publishEvent(new NotePublishedEvent(100L, 7L));
    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.QUOTE,
                8L,
                7L,
                null,
                50L,
                "original",
                100L,
                "so true",
                50L));
  }

  @Test
  void aNoteQuotesAPostOrANoteButNeverBoth() {
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", null, 5L, null, 50L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_QUOTE_CONFLICT));
    verify(notes, never()).save(any());
  }

  @Test
  void aQuotedNoteMustExistHaveAnActiveAuthorAndNotCrossABlock() {
    when(notes.findById(51L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", null, null, null, 51L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND));

    when(notes.findById(50L)).thenReturn(Optional.of(note(50L, 8L, "original")));
    when(people.activeAuthors(Set.of(7L, 8L))).thenReturn(Map.of(7L, WRITER));
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", null, null, null, 50L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND));

    when(blocks.isBlocked(8L, 7L)).thenReturn(true);
    assertThatThrownBy(() -> service().create(7L, new NoteDraft("x", null, null, null, 50L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_INTERACTION_BLOCKED));
    verify(notes, never()).save(any());
  }

  @Test
  void onlyARealRepostChangeIsAnnouncedAndEveryoneGetsTheCount() {
    when(notes.findById(1L)).thenReturn(Optional.of(note(1L, 7L, "x")));
    NoteRepostEntity repost = new NoteRepostEntity(1L, 8L);
    ReflectionTestUtils.setField(repost, "id", 900L);
    when(reposts.addIfAbsent(1L, 8L)).thenReturn(Optional.of(repost), Optional.empty());
    when(reposts.delete(1L, 8L)).thenReturn(Optional.of(repost), Optional.empty());
    when(notes.stats(List.of(1L))).thenReturn(Map.of(1L, new NoteStats(0, 0, 3, 0)));

    assertThat(service().setRepost(8L, 1L, true))
        .isEqualTo(new NoteCommandService.RepostStatus(true, 3L));
    service().setRepost(8L, 1L, true);
    assertThat(service().setRepost(8L, 1L, false))
        .isEqualTo(new NoteCommandService.RepostStatus(false, 3L));
    service().setRepost(8L, 1L, false);

    verify(moderation, times(2)).requireCanWrite(8L);
    verify(events, times(1)).publishEvent(new NoteRepostedEvent(900L, 1L, 8L));
    verify(events, times(1)).publishEvent(new NoteUnrepostedEvent(900L, 1L, 8L));

    when(reposts.addIfAbsent(1L, 7L)).thenReturn(Optional.empty());
    assertThat(service().setRepost(7L, 1L, true))
        .isEqualTo(new NoteCommandService.RepostStatus(true, 3L));
  }

  @Test
  void aBlockEitherWayStopsARepost() {
    when(notes.findById(1L)).thenReturn(Optional.of(note(1L, 7L, "x")));
    when(blocks.isBlocked(7L, 8L)).thenReturn(true);

    assertThatThrownBy(() -> service().setRepost(8L, 1L, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_INTERACTION_BLOCKED));
    verify(reposts, never()).addIfAbsent(any(), any());
  }

  @Test
  void aNoteFromAnotherServerIsLikedRepostedAndAnsweredWithItsServerToldButNotQuotedYet() {
    NoteEntity remote = note(1L, null, "from afar");
    ReflectionTestUtils.setField(remote, "remoteActorId", 42L);
    when(notes.findById(1L)).thenReturn(Optional.of(remote));
    when(likes.addIfAbsent(1L, 8L)).thenReturn(true);
    when(notes.stats(List.of(1L))).thenReturn(Map.of());
    NoteRepostEntity repost = new NoteRepostEntity(1L, 8L);
    ReflectionTestUtils.setField(repost, "id", 77L);
    when(reposts.addIfAbsent(1L, 8L)).thenReturn(Optional.of(repost));
    when(reposts.delete(1L, 8L)).thenReturn(Optional.of(repost));
    when(people.activeAuthors(Set.of(8L))).thenReturn(Map.of(8L, new NoteAuthor(8L, "me", null)));
    saving();

    assertThat(service().setLike(8L, 1L, true).liked()).isTrue();
    service().setLike(8L, 1L, false);
    assertThat(service().setBookmark(8L, 1L, true).bookmarked()).isTrue();
    service().setRepost(8L, 1L, true);
    service().setRepost(8L, 1L, false);
    service().create(8L, new NoteDraft("an answer", null, null, 1L, null));

    verify(events).publishEvent(new RemoteNoteLikedEvent(1L, 8L, true));
    verify(events).publishEvent(new RemoteNoteLikedEvent(1L, 8L, false));
    verify(events).publishEvent(new NoteRepostedEvent(77L, 1L, 8L, true));
    verify(events).publishEvent(new NoteUnrepostedEvent(77L, 1L, 8L, true));
    verify(events).publishEvent(new NotePublishedEvent(100L, 8L, true));
    assertThatThrownBy(() -> service().create(8L, new NoteDraft("hi", null, null, null, 1L)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_REMOTE_UNSUPPORTED));
    verify(blocks, never()).isBlocked(any(), any());
  }

  @Test
  void aNoteNamingSomeoneElsewhereIsMarkedToReachTheirServer() {
    saving();
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));

    service().create(7L, new NoteDraft("hi @bob@mastodon.social", null, null, null));

    verify(events).publishEvent(new NotePublishedEvent(100L, 7L, true));
  }

  @Test
  void aNoteWithAnAddressAsksForItsCardButPhotosAndQuotesAlreadyCarryOne() {
    saving();
    service().create(7L, new NoteDraft("see https://example.com/a.", null, null, null));
    verify(events).publishEvent(new NoteLinkPreviewRequested(100L, "https://example.com/a"));

    when(images.verify(any(), any()))
        .thenReturn(new NoteImages.StoredImage("k", "u", "image/png", null));
    service()
        .create(
            7L,
            new NoteDraft(
                "photo https://example.com/b",
                List.of(new NoteDraft.Image("k", null)),
                null,
                null));
    verify(events, never())
        .publishEvent(new NoteLinkPreviewRequested(100L, "https://example.com/b"));
  }

  @Test
  void editingAsksAgainOnlyWhenTheAddressChanges() {
    NoteEntity mine = note(1L, 7L, "old https://example.com/a");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));

    service().edit(7L, 1L, "typo fixed https://example.com/a", null, null);
    verify(events, never()).publishEvent(any(NoteLinkPreviewRequested.class));

    service().edit(7L, 1L, "now https://example.com/b", null, null);
    verify(events).publishEvent(new NoteLinkPreviewRequested(1L, "https://example.com/b"));

    service().edit(7L, 1L, "no address any more", null, null);
    verify(events).publishEvent(new NoteLinkPreviewRequested(1L, null));
  }

  @Test
  void aNoteKeepsItsHashtagsAndAnEditRewritesThemOnlyWhenTheSetChanges() {
    saving();
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));
    service().create(7L, new NoteDraft("오늘 #스프링 #Boot", List.of(), null, null));
    verify(notes).tag(100L, List.of("스프링", "Boot"));

    NoteEntity mine = note(1L, 7L, "#a #b");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));

    service().edit(7L, 1L, "#B then #A", null, null);
    verify(notes, never()).retag(any(), any());

    service().edit(7L, 1L, "only #c now", null, null);
    verify(notes).retag(1L, List.of("c"));
  }

  @Test
  void mentionedMembersAreToldOnceAndTheRepliedToAuthorOnlyThroughTheReply() {
    saving();
    when(notes.findById(5L)).thenReturn(Optional.of(note(5L, 9L, "parent")));
    NoteAuthor mina = new NoteAuthor(9L, "mina", null);
    NoteAuthor yuki = new NoteAuthor(11L, "yuki", null);
    when(people.activeAuthors(Set.of(7L), List.of("mina", "yuki", "writer", "ghost")))
        .thenReturn(Map.of(7L, WRITER, 9L, mina, 11L, yuki));

    NoteView view =
        service().create(7L, new NoteDraft("@mina @Yuki @writer @ghost 안녕", List.of(), null, 5L));

    assertThat(view.mentions()).containsExactly("mina", "yuki", "writer");
    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.MENTION,
                11L,
                7L,
                null,
                100L,
                "@mina @Yuki @writer @ghost 안녕",
                null,
                null,
                5L));
    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(published.capture());
    assertThat(published.getAllValues())
        .filteredOn(NoteInteractionEvent.class::isInstance)
        .map(NoteInteractionEvent.class::cast)
        .filteredOn(event -> event.type() == NoteInteractionEvent.Type.MENTION)
        .extracting(NoteInteractionEvent::recipientUserId)
        .containsExactly(11L);
  }

  @Test
  void anEditTellsOnlyMembersItNewlyMentions() {
    NoteEntity mine = note(1L, 7L, "@mina hi");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));
    when(people.activeAuthors(List.of(), List.of("yuki")))
        .thenReturn(Map.of(11L, new NoteAuthor(11L, "yuki", null)));

    service().edit(7L, 1L, "@mina and @yuki hi", null, null);

    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.MENTION,
                11L,
                7L,
                null,
                1L,
                "@mina and @yuki hi",
                null,
                null,
                1L));
  }

  @Test
  void aWarningIsTrimmedCappedAndAlwaysHidesThePhotosToo() {
    saving();
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));

    NoteView warned =
        service().create(7L, new NoteDraft("결말", List.of(), null, null, null, "  스포일러  ", false));
    assertThat(warned.contentWarning()).isEqualTo("스포일러");
    assertThat(warned.sensitive()).isTrue();

    NoteView plain =
        service().create(7L, new NoteDraft("평범", List.of(), null, null, null, "   ", false));
    assertThat(plain.contentWarning()).isNull();
    assertThat(plain.sensitive()).isFalse();

    assertThatThrownBy(
            () ->
                service()
                    .create(
                        7L,
                        new NoteDraft("x", List.of(), null, null, null, "가".repeat(101), false)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_WARNING_TOO_LONG));
  }

  @Test
  void anEditKeepsTheWarningUnlessItSendsOneAndAnEmptyWarningRemovesIt() {
    NoteEntity mine = note(1L, 7L, "old");
    mine.markContent("스포일러", false);
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));

    service().edit(7L, 1L, "body only", null, null);
    assertThat(mine.getContentWarning()).isEqualTo("스포일러");

    service().edit(7L, 1L, "no warning", "", false);
    assertThat(mine.getContentWarning()).isNull();
    assertThat(mine.isSensitive()).isFalse();
  }

  @Test
  void anAuthorPinsUpToFiveTopLevelNotesAndPinningAgainKeepsTheFirstPin() {
    NoteEntity mine = note(1L, 7L, "pin me");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(notes.countPinned(7L)).thenReturn(4L);

    assertThat(service().setPin(7L, 1L, true).pinned()).isTrue();
    assertThat(mine.getPinnedAt()).isEqualTo(Instant.parse("2026-10-06T00:00:00.123456Z"));
    assertThat(service().setPin(7L, 1L, true).pinned()).isTrue();
    assertThat(service().setPin(7L, 1L, false).pinned()).isFalse();
    assertThat(mine.isPinned()).isFalse();

    when(notes.countPinned(7L)).thenReturn(5L);
    assertThatThrownBy(() -> service().setPin(7L, 1L, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_PIN_LIMIT));

    NoteEntity reply = new NoteEntity(7L, "reply", 9L, null);
    ReflectionTestUtils.setField(reply, "id", 2L);
    when(notes.findById(2L)).thenReturn(Optional.of(reply));
    assertThatThrownBy(() -> service().setPin(7L, 2L, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_PIN_REPLY));
    assertThatThrownBy(() -> service().setPin(8L, 1L, true)).isInstanceOf(NoteException.class);
  }

  @Test
  void anEditThatChangesTheNoteKeepsThePreviousVersionAndAnUnchangedOneDoesNot() {
    NoteEntity mine = note(1L, 7L, "처음");
    ReflectionTestUtils.setField(mine, "createdAt", Instant.parse("2026-10-01T00:00:00Z"));
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    when(views.of(anyList(), any())).thenReturn(List.of(org.mockito.Mockito.mock(NoteView.class)));

    service().edit(7L, 1L, "처음", null, null);
    verify(notes, never()).recordVersion(any(), any());

    service().edit(7L, 1L, "고친 글", null, null);
    verify(notes)
        .recordVersion(
            1L, new NoteVersion("처음", null, false, Instant.parse("2026-10-01T00:00:00Z")));

    service().edit(7L, 1L, "고친 글", "스포일러", null);
    verify(notes)
        .recordVersion(
            1L, new NoteVersion("고친 글", null, false, Instant.parse("2026-10-06T00:00:00.123456Z")));
  }

  @Test
  void aReplyKeepsItsParentsVisibilityAndARestrictedNoteRecordsWhomItMentions() {
    saving();
    NoteEntity parent = note(5L, 9L, "followers only");
    parent.showTo(NoteVisibility.PRIVATE);
    when(notes.findById(5L)).thenReturn(Optional.of(parent));
    when(notes.visibleTo(7L, Set.of(5L))).thenReturn(Set.of(5L));
    NoteAuthor mina = new NoteAuthor(11L, "mina", null);
    when(people.activeAuthors(Set.of(7L), List.of("mina")))
        .thenReturn(Map.of(7L, WRITER, 11L, mina));

    NoteView reply = service().create(7L, new NoteDraft("@mina 같이 봐요", List.of(), null, 5L));

    assertThat(reply.visibility()).isEqualTo("private");
    verify(notes).addRecipients(100L, List.of(11L));
  }

  @Test
  void aHiddenNoteIsNotFoundAndOnlyPublicOrUnlistedNotesAreReposted() {
    NoteEntity hidden = note(5L, 9L, "secret");
    hidden.showTo(NoteVisibility.DIRECT);
    when(notes.findById(5L)).thenReturn(Optional.of(hidden));
    assertThatThrownBy(() -> service().setLike(7L, 5L, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_NOT_FOUND));

    NoteEntity followersOnly = note(6L, 9L, "followers");
    followersOnly.showTo(NoteVisibility.PRIVATE);
    when(notes.findById(6L)).thenReturn(Optional.of(followersOnly));
    when(notes.visibleTo(7L, Set.of(6L))).thenReturn(Set.of(6L));
    assertThatThrownBy(() -> service().setRepost(7L, 6L, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_NOT_SHAREABLE));

    assertThatThrownBy(
            () ->
                service()
                    .create(
                        7L,
                        new NoteDraft("x", List.of(), null, null, null, null, false, "everyone")))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_VISIBILITY_INVALID));
  }

  @Test
  void onlyTheAuthorEditsAndTheEditIsStampedToMicroseconds() {
    NoteEntity mine = note(1L, 7L, "old");
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(media.findByNoteIds(List.of(1L))).thenReturn(List.of());
    NoteView edited =
        new NoteView(
            1L, "new", null, null, 0L, false, WRITER, List.of(), null, null, 0, null, null, null,
            null);
    when(views.of(List.of(mine), 7L)).thenReturn(List.of(edited));

    assertThat(service().edit(7L, 1L, " new ", null, null)).isEqualTo(edited);
    assertThat(mine.getBody()).isEqualTo("new");
    assertThat(mine.getEditedAt()).isEqualTo(Instant.parse("2026-10-06T00:00:00.123456Z"));
    verify(events).publishEvent(new NoteEditedEvent(1L, 7L));

    assertThatThrownBy(() -> service().edit(8L, 1L, "theirs", null, null))
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

    service().edit(7L, 1L, "", null, null);

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
  void likeCountsAreReturnedToEveryone() {
    when(notes.findById(1L)).thenReturn(Optional.of(note(1L, 7L, "x")));
    when(notes.stats(List.of(1L))).thenReturn(Map.of(1L, new NoteStats(0, 5, 0, 0)));
    when(likes.addIfAbsent(1L, 7L)).thenReturn(false);
    when(likes.addIfAbsent(1L, 8L)).thenReturn(true, false);

    assertThat(service().setLike(7L, 1L, true))
        .isEqualTo(new NoteCommandService.LikeStatus(true, 5L));
    assertThat(service().setLike(8L, 1L, true))
        .isEqualTo(new NoteCommandService.LikeStatus(true, 5L));
    assertThat(service().setLike(8L, 1L, false))
        .isEqualTo(new NoteCommandService.LikeStatus(false, 5L));
    service().setLike(8L, 1L, true);
    verify(likes).delete(1L, 8L);
    verify(events, times(1))
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.LIKE, 7L, 8L, null, 1L, "x", null, null, 1L));
  }

  @Test
  void mutingAConversationCoversTheWholeThreadFromAnyNoteInIt() {
    NoteEntity reply = note(60L, 8L, "a reply");
    reply.answer(note(50L, 7L, "root"));
    when(notes.findById(60L)).thenReturn(Optional.of(reply));
    when(notes.findById(50L)).thenReturn(Optional.of(note(50L, 7L, "root")));

    assertThat(service().setConversationMuted(9L, 60L, true).muted()).isTrue();
    assertThat(service().setConversationMuted(9L, 50L, false).muted()).isFalse();

    verify(notes).muteConversation(9L, 50L, NOW);
    verify(notes).unmuteConversation(9L, 50L);
  }

  @Test
  void aReplyToAReplyJoinsTheRootsConversation() {
    saving();
    NoteEntity middle = note(60L, 8L, "middle");
    middle.answer(note(50L, 8L, "root"));
    when(notes.findById(60L)).thenReturn(Optional.of(middle));
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));

    service().create(7L, new NoteDraft("deeper", null, null, 60L));

    ArgumentCaptor<NoteEntity> saved = ArgumentCaptor.forClass(NoteEntity.class);
    verify(notes).save(saved.capture());
    assertThat(saved.getValue().getConversationId()).isEqualTo(50L);
    assertThat(saved.getValue().conversation()).isEqualTo(50L);
  }

  @Test
  void aTakeDownDeletesAMembersNoteForEveryoneAndANoteFromElsewhereOnlyHere() {
    NoteEntity mine = note(1L, 7L, "x");
    NoteEntity remote = note(2L, null, "y");
    ReflectionTestUtils.setField(remote, "remoteActorId", 42L);
    when(notes.findById(1L)).thenReturn(Optional.of(mine));
    when(notes.findById(2L)).thenReturn(Optional.of(remote));
    when(notes.findById(3L)).thenReturn(Optional.empty());
    when(media.findByNoteIds(List.of(1L)))
        .thenReturn(List.of(new NoteMediaEntity(1L, 0, "k1", "https://cdn/k1", "image/png", null)));
    when(media.findByNoteIds(List.of(2L)))
        .thenReturn(
            List.of(new NoteMediaEntity(2L, 0, "", "https://elsewhere/a.png", "image/png", null)));

    service().takeDown(1L);
    service().takeDown(2L);
    service().takeDown(3L);

    verify(notes).delete(mine);
    verify(notes).delete(remote);
    verify(likes).deleteAllByNoteId(2L);
    verify(events).publishEvent(new NoteDeletedEvent(1L, 7L, List.of("k1")));
    verify(events, never()).publishEvent(new NoteDeletedEvent(2L, null, List.of()));
  }

  @Test
  void aBookmarkIsTheReadersOwnAndQuiet() {
    when(notes.findById(1L)).thenReturn(Optional.of(note(1L, 7L, "x")));

    assertThat(service().setBookmark(8L, 1L, true))
        .isEqualTo(new NoteCommandService.BookmarkStatus(true));
    assertThat(service().setBookmark(8L, 1L, false))
        .isEqualTo(new NoteCommandService.BookmarkStatus(false));

    verify(bookmarks).addIfAbsent(1L, 8L);
    verify(bookmarks).delete(1L, 8L);
    verifyNoInteractions(events);
  }

  private static NoteDraft polled(List<String> options, Long expiresIn, boolean multiple) {
    return new NoteDraft(
        "어디서 볼까?",
        List.of(),
        null,
        null,
        null,
        null,
        false,
        null,
        new NoteDraft.Poll(options, expiresIn, multiple));
  }

  @Test
  void aPollKeepsItsTrimmedOptionsAndEndsAfterItsDuration() {
    saving();
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));

    NoteView view = service().create(7L, polled(List.of("  강남 ", "홍대\n입구"), 3600L, true));

    ArgumentCaptor<NoteEntity> saved = ArgumentCaptor.forClass(NoteEntity.class);
    verify(notes).save(saved.capture());
    assertThat(saved.getValue().pollOptions()).containsExactly("강남", "홍대 입구");
    assertThat(saved.getValue().isPollMultiple()).isTrue();
    assertThat(view.poll().expiresAt())
        .isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS).plusSeconds(3600));
    assertThat(view.poll().options())
        .extracting(NoteView.PollOption::title, NoteView.PollOption::votesCount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("강남", 0L),
            org.assertj.core.groups.Tuple.tuple("홍대 입구", 0L));
    assertThat(view.poll().voted()).isTrue();
    assertThat(view.poll().expired()).isFalse();
  }

  @Test
  void aPollTakesTheMediaSlotSoItsAddressGetsNoCard() {
    saving();
    when(people.activeAuthors(Set.of(7L))).thenReturn(Map.of(7L, WRITER));
    NoteDraft draft =
        new NoteDraft(
            "어느 쪽? https://kurl.me/about",
            List.of(),
            null,
            null,
            null,
            null,
            false,
            null,
            new NoteDraft.Poll(List.of("a", "b"), 3600L, false));

    service().create(7L, draft);

    verify(events, never()).publishEvent(any(NoteLinkPreviewRequested.class));
  }

  @Test
  void aPollNeedsTwoToFourDifferentShortOptionsAFittingDurationAndNoImages() {
    String long51 = "가".repeat(51);
    for (NoteDraft draft :
        List.of(
            polled(List.of("하나"), 3600L, false),
            polled(List.of("a", "b", "c", "d", "e"), 3600L, false),
            polled(List.of("같다", " 같다 "), 3600L, false),
            polled(List.of("a", "  "), 3600L, false),
            polled(List.of("a", long51), 3600L, false),
            polled(null, 3600L, false),
            polled(List.of("a", "b"), null, false),
            polled(List.of("a", "b"), 299L, false),
            polled(List.of("a", "b"), 2_629_747L, false))) {
      assertThatThrownBy(() -> service().create(7L, draft))
          .isInstanceOfSatisfying(
              NoteException.class,
              e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_POLL_INVALID));
    }
    NoteDraft withImage =
        new NoteDraft(
            "x",
            List.of(new NoteDraft.Image("k1", null)),
            null,
            null,
            null,
            null,
            false,
            null,
            new NoteDraft.Poll(List.of("a", "b"), 3600L, false));
    assertThatThrownBy(() -> service().create(7L, withImage))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_POLL_WITH_MEDIA));
    verify(notes, never()).save(any());
  }
}
