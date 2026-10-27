package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.post.SeriesItemCleaner;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViews;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinkPreviewEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteAccessReader;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// The notes' response JSON as it reads today, field by field: what a feed page shows a signed-in
// reader and an anonymous one, and what posting a note answers.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteViewJsonGoldenTest {

  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00.123456Z");
  private static final Path GOLDEN = Path.of("src/test/resources/note-view-golden");
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
  private static final NoteAuthor ME = new NoteAuthor(7L, "me", "https://cdn/me.png");
  private static final NoteAuthor MINA = new NoteAuthor(9L, "mina", null);
  private static final NoteAuthor ALICE =
      NoteAuthor.remote(
          42L, "alice@m.example", "https://m.example/a.png", "Alice", "https://m.example/@alice");

  @Mock private NoteRepository notes;
  @Mock private NotePageReader pages;
  @Mock private NoteAccessReader lookups;
  @Mock private NoteRoomReader rooms;
  @Mock private NoteMediaRepository media;
  @Mock private NotePeopleReader people;
  @Mock private QuotedPostReader quotedPosts;
  @Mock private NoteLinkPreviewRepository linkPreviews;
  @Mock private NotePollRepository polls;
  @Mock private NoteLikeRepository likes;
  @Mock private NoteRepostRepository reposts;
  @Mock private NoteBookmarkRepository bookmarks;
  @Mock private NoteImages images;
  @Mock private UserModerationGuard moderation;
  @Mock private UserBlockChecker blocks;
  @Mock private CollectionConnectionCleaner connections;
  @Mock private SeriesItemCleaner seriesItems;
  @Mock private ApplicationEventPublisher events;
  @Mock private RoomNoteErasureRepository erasures;

  private NoteViews views;

  @BeforeEach
  void views() {
    views =
        new NoteViews(
            notes,
            pages,
            new NoteAccess(lookups),
            rooms,
            media,
            people,
            quotedPosts,
            linkPreviews,
            polls,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private NoteComposer composer() {
    return new NoteComposer(
        notes,
        media,
        quotedPosts,
        people,
        images,
        views,
        new NoteAccess(lookups),
        rooms,
        moderation,
        new NoteWriteGuard(notes, new NoteAccess(lookups), blocks),
        events,
        FakeTransactions.template(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static NoteEntity note(Long id, Long userId, String body, int minutesAgo) {
    NoteEntity note = new NoteEntity(userId, body, null, null);
    ReflectionTestUtils.setField(note, "id", id);
    ReflectionTestUtils.setField(note, "createdAt", NOW.minusSeconds(60L * minutesAgo));
    return note;
  }

  private static NoteEntity remote(Long id, String url, int minutesAgo) {
    NoteEntity note = note(id, null, "from afar " + id, minutesAgo);
    ReflectionTestUtils.setField(note, "remoteActorId", 42L);
    ReflectionTestUtils.setField(note, "uri", "https://m.example/notes/" + id);
    ReflectionTestUtils.setField(note, "remoteUrl", url);
    return note;
  }

  private static NoteMediaEntity image(Long noteId, int position) {
    return new NoteMediaEntity(
        noteId,
        position,
        "note-images/" + noteId + "/" + position + ".png",
        "https://cdn/note-images/" + noteId + "/" + position + ".png",
        "image/png",
        position == 0 ? "창밖" : null,
        position == 0 ? 1200 : null,
        position == 0 ? 900 : null);
  }

  private static NoteLinkPreviewEntity card(Long noteId) {
    NoteLinkPreviewEntity card = BeanUtils.instantiateClass(NoteLinkPreviewEntity.class);
    ReflectionTestUtils.setField(card, "noteId", noteId);
    ReflectionTestUtils.setField(card, "url", "https://example.com/a");
    ReflectionTestUtils.setField(card, "title", "Example");
    ReflectionTestUtils.setField(card, "description", "An example page");
    ReflectionTestUtils.setField(card, "imageUrl", "https://example.com/a.png");
    return card;
  }

  private List<NoteEntity> page() {
    NoteEntity rich = note(1L, 7L, "@mina @nobody 보세요 https://example.com/a #산책", 50);
    rich.markContent("스포일러", true);
    rich.writeIn("ko");
    rich.pin(NOW.minusSeconds(10));
    ReflectionTestUtils.setField(rich, "quotedPostId", 5L);
    NoteEntity quoting = note(2L, 9L, "인용합니다", 40);
    ReflectionTestUtils.setField(quoting, "quotedNoteId", 10L);
    ReflectionTestUtils.setField(quoting, "editedAt", NOW.minusSeconds(60));
    NoteEntity article = remote(3L, "https://m.example/@alice/3", 30);
    article.describeElsewhere("ARTICLE", "A long read", "books");
    NoteEntity question = remote(4L, "gopher://m.example/4", 25);
    question.pollElsewhere(
        List.of("yes", "no"), NOW.plusSeconds(3600), false, List.of(3L, 1L), 4, null);
    NoteEntity inRoom = note(5L, 7L, "방에서", 20);
    inRoom.postIn(30L);
    inRoom.showTo(NoteVisibility.ROOM);
    NoteEntity closed = note(6L, 9L, "말 걸지 마세요", 15);
    closed.limitReplies(NoteReplyPolicy.MENTIONED);
    NoteEntity voting = note(8L, 7L, "골라 주세요", 10);
    voting.attachPoll(List.of("a", "b", "c"), NOW.plusSeconds(600), true);
    NoteEntity hidden = note(11L, 9L, "숨긴 답글", 5);
    ReflectionTestUtils.setField(hidden, "inReplyToId", 6L);
    ReflectionTestUtils.setField(hidden, "conversationId", 6L);
    ReflectionTestUtils.setField(hidden, "reply", true);
    hidden.hideReply(NOW.minusSeconds(30));
    return List.of(rich, quoting, article, question, inRoom, closed, voting, hidden);
  }

  private void lookupsForThePage() {
    NoteEntity quoted = note(10L, 9L, "인용된 노트", 120);
    quoted.markContent(null, true);
    when(notes.findAllByIdIn(anyCollection()))
        .thenAnswer(
            inv -> {
              List<NoteEntity> found = new ArrayList<>();
              if (inv.<java.util.Collection<Long>>getArgument(0).contains(10L)) {
                found.add(quoted);
              }
              return found;
            });
    when(people.activeAuthors(anyCollection(), anyCollection()))
        .thenReturn(Map.of(7L, ME, 9L, MINA));
    when(people.activeAuthors(anyCollection())).thenReturn(Map.of(7L, ME, 9L, MINA));
    when(people.remoteAuthors(anyCollection(), any())).thenReturn(Map.of(42L, ALICE));
    when(people.followersOf(any(), anyCollection())).thenReturn(Set.of());
    when(lookups.visibleTo(any(), anyCollection())).thenReturn(Set.of(5L));
    when(media.findByNoteIds(anyCollection()))
        .thenReturn(List.of(image(1L, 0), image(1L, 1), image(10L, 0)));
    when(quotedPosts.publishedByIds(any()))
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "writer", 3L)));
    when(pages.stats(any()))
        .thenReturn(Map.of(1L, new NoteStats(2, 5, 1, 3), 3L, new NoteStats(0, 7, 2, 0)));
    when(pages.viewerMarks(any(), any()))
        .thenReturn(new NoteViewerMarks(Set.of(1L), Set.of(3L), Set.of(2L), Set.of(6L)));
    when(linkPreviews.findByNoteIds(any())).thenReturn(List.of(card(1L)));
    when(polls.tallies(any(), any()))
        .thenReturn(Map.of(8L, new NotePollTally(5, List.of(2L, 3L, 1L), 0b10)));
    when(rooms.names(any())).thenReturn(Map.of(30L, "책 모임"));
  }

  @Test
  void aFeedPageReadsAsItDidForASignedInReader() throws IOException {
    lookupsForThePage();

    assertGolden("feed-signed-in.json", views.of(page(), 7L));
  }

  @Test
  void aFeedPageReadsAsItDidForAnAnonymousReader() throws IOException {
    lookupsForThePage();
    when(lookups.visibleTo(any(), anyCollection())).thenReturn(Set.of());

    assertGolden("feed-anonymous.json", views.of(page(), null));
  }

  @Test
  void postedNotesAnswerAsTheyDid() throws IOException {
    AtomicLong ids = new AtomicLong(100L);
    when(notes.save(any()))
        .thenAnswer(
            inv -> {
              NoteEntity note = inv.getArgument(0);
              ReflectionTestUtils.setField(note, "id", ids.getAndIncrement());
              ReflectionTestUtils.setField(note, "createdAt", NOW);
              return note;
            });
    NoteEntity alices = remote(20L, "https://m.example/@alice/20", 60);
    NoteEntity parent = note(21L, 9L, "질문", 90);
    when(notes.findById(20L)).thenReturn(Optional.of(alices));
    when(notes.findById(21L)).thenReturn(Optional.of(parent));
    when(people.activeAuthors(anyCollection())).thenReturn(Map.of(7L, ME, 9L, MINA));
    when(people.activeAuthors(anyCollection(), anyCollection()))
        .thenReturn(Map.of(7L, ME, 9L, MINA));
    when(people.remoteAuthors(anyCollection(), any())).thenReturn(Map.of(42L, ALICE));
    when(lookups.readersAmong(any(), anyCollection())).thenReturn(Set.of(9L));
    when(media.findByNoteIds(anyCollection())).thenReturn(List.of(image(20L, 0)));
    when(quotedPosts.publishedByIds(any()))
        .thenReturn(Map.of(5L, new QuotedPost(5L, "Essay", "essay", "writer", 3L)));
    when(images.verifyAll(any(), any()))
        .thenAnswer(
            inv ->
                inv.<List<NoteDraft.Image>>getArgument(1) == null
                    ? List.of()
                    : List.of(
                        new NoteImages.StoredImage(
                            "note-images/7/a.png",
                            "https://cdn/note-images/7/a.png",
                            "image/png",
                            "고양이",
                            800,
                            600)));
    when(rooms.isMember(30L, 7L)).thenReturn(true);
    when(rooms.names(any())).thenReturn(Map.of(30L, "책 모임"));
    NoteComposer command = composer();

    List<NoteView> posted =
        List.of(
            command.create(
                7L,
                new NoteDraft(
                    "사진과 글 @mina https://example.com/b",
                    List.of(new NoteDraft.Image("note-images/7/a.png", "고양이")),
                    5L,
                    null,
                    null,
                    "약간의 스포일러",
                    false,
                    "unlisted",
                    null,
                    "ko",
                    "mentioned")),
            command.create(
                7L, new NoteDraft("앨리스 글을 인용", null, null, null, 20L, null, true, null, null)),
            command.create(
                7L,
                new NoteDraft(
                    "방에서 투표",
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    "room",
                    new NoteDraft.Poll(List.of("하나", "둘"), 3600L, false),
                    null,
                    null,
                    30L)),
            command.create(7L, new NoteDraft("답합니다 @mina", null, null, 21L)));

    assertGolden("create.json", posted);
  }

  private static void assertGolden(String name, List<NoteView> views) throws IOException {
    String actual = JSON.writeValueAsString(views) + "\n";
    assertThat(actual).isEqualTo(Files.readString(GOLDEN.resolve(name), StandardCharsets.UTF_8));
  }
}
