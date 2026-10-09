package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.groups.Tuple.tuple;

import com.example.short_link.common.post.SeriesItemCleaner;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteSeries;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteSeriesReader;
import com.example.short_link.post.domain.FollowingFeedRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.FollowingFeedReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeriesItemPersistenceIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private NoteRepository noteRepository;
  @Autowired private SeriesItemRepository seriesItemRepository;
  @Autowired private SeriesItemReader seriesItemReader;
  @Autowired private NoteSeriesReader noteSeriesReader;
  @Autowired private SeriesItemCleaner seriesItemCleaner;
  @Autowired private FollowingFeedReader followingFeedReader;

  private Long authorId;
  private Long seriesId;
  private Long otherSeriesId;

  @BeforeEach
  void setUp() {
    UserEntity author = new UserEntity("sit701@x.com", "google", "g-sit701");
    author.claimUsername("sit701");
    authorId = userRepository.save(author).getId();
    seriesId = seriesRepository.save(new SeriesEntity(authorId, "sit701-a", "A")).getId();
    otherSeriesId = seriesRepository.save(new SeriesEntity(authorId, "sit701-b", "B")).getId();
  }

  private Long post(String slug, boolean published) {
    PostEntity post = new PostEntity(authorId, slug, slug.toUpperCase(), "ko");
    if (published) post.publish();
    post.assignToSeries(seriesId, 0);
    return postRepository.save(post).getId();
  }

  private Long note(String body, NoteVisibility visibility, String warning) {
    NoteEntity note = new NoteEntity(authorId, body, null, null);
    note.showTo(visibility);
    if (warning != null) note.markContent(warning, false);
    return noteRepository.save(note).getId();
  }

  private List<String> itemsOf(Long series) {
    return seriesItemRepository.findBySeriesId(series).stream()
        .map(i -> i.getType() + ":" + i.getRefId())
        .toList();
  }

  @Test
  void aNoteMovesBetweenSeriesInsteadOfSittingInTwo() {
    Long post = post("sit701-p", true);
    Long note = note("moving note", NoteVisibility.PUBLIC, null);
    seriesItemRepository.replace(
        seriesId,
        List.of(
            new SeriesItemEntity(seriesId, SeriesItemType.POST, post, 0),
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, note, 1)));

    seriesItemRepository.replace(
        otherSeriesId, List.of(new SeriesItemEntity(otherSeriesId, SeriesItemType.NOTE, note, 0)));

    assertThat(itemsOf(seriesId)).containsExactly("POST:" + post);
    assertThat(itemsOf(otherSeriesId)).containsExactly("NOTE:" + note);
    assertThat(seriesItemRepository.findBySeriesIdIn(List.of(seriesId, otherSeriesId))).hasSize(2);
    assertThat(seriesItemRepository.findBySeriesIdIn(List.of())).isEmpty();
  }

  @Test
  void readersSeePublishedPostsAndPublicOrUnlistedNotesWithWarningsInPlaceOfText() {
    Long published = post("sit701-live", true);
    Long draft = post("sit701-draft", false);
    Long open = note("an   open\nnote", NoteVisibility.PUBLIC, null);
    Long warned = note("spoils the ending", NoteVisibility.UNLISTED, "ending spoilers");
    Long followersOnly = note("for followers", NoteVisibility.PRIVATE, null);
    seriesItemRepository.replace(
        seriesId,
        List.of(
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, open, 0),
            new SeriesItemEntity(seriesId, SeriesItemType.POST, draft, 1),
            new SeriesItemEntity(seriesId, SeriesItemType.POST, published, 2),
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, followersOnly, 3),
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, warned, 4)));

    assertThat(seriesItemReader.readableEntries(seriesId))
        .extracting(SeriesEntry::type, SeriesEntry::refId, SeriesEntry::slug, SeriesEntry::title)
        .containsExactly(
            tuple(SeriesItemType.NOTE, open, null, "an open note"),
            tuple(SeriesItemType.POST, published, "sit701-live", "SIT701-LIVE"),
            tuple(SeriesItemType.NOTE, warned, null, "ending spoilers"));
    assertThat(seriesItemReader.readableEntries(List.of(seriesId, otherSeriesId)))
        .containsOnlyKeys(seriesId);
    assertThat(seriesItemReader.notes(List.of(open, followersOnly)))
        .hasEntrySatisfying(open, n -> assertThat(n.shared()).isTrue())
        .hasEntrySatisfying(followersOnly, n -> assertThat(n.shared()).isFalse());
    assertThat(seriesItemReader.notes(List.of())).isEmpty();
    assertThat(seriesItemReader.notes(List.of(open)).get(open).createdAt())
        .isCloseTo(
            noteRepository.findById(open).orElseThrow().getCreatedAt(),
            within(1, ChronoUnit.SECONDS));

    NoteSeries series = noteSeriesReader.containing(warned).orElseThrow();
    assertThat(series.slug()).isEqualTo("sit701-a");
    assertThat(series.entries())
        .extracting(NoteSeries.Entry::type, NoteSeries.Entry::refId, NoteSeries.Entry::title)
        .containsExactly(
            tuple("NOTE", open, "an open note"),
            tuple("POST", published, "SIT701-LIVE"),
            tuple("NOTE", warned, "ending spoilers"));
  }

  private void place(Long series, Object... typeAndIds) {
    List<SeriesItemEntity> rows = new ArrayList<>();
    for (int i = 0; i < typeAndIds.length; i += 2) {
      rows.add(
          new SeriesItemEntity(
              series, (SeriesItemType) typeAndIds[i], (Long) typeAndIds[i + 1], rows.size()));
    }
    seriesItemRepository.replace(series, rows);
  }

  @Test
  void discoveryCountsReadableNotesAndPostsPastTheFloorOnly() {
    Long shortPost = post("sit701-short", true);
    Long a1 = note("first aside", NoteVisibility.PUBLIC, null);
    Long a2 = note("second aside", NoteVisibility.UNLISTED, null);
    place(
        seriesId, SeriesItemType.POST, shortPost, SeriesItemType.NOTE, a1, SeriesItemType.NOTE, a2);
    Long b1 = note("open", NoteVisibility.PUBLIC, null);
    Long b2 = note("followers only", NoteVisibility.PRIVATE, null);
    place(otherSeriesId, SeriesItemType.NOTE, b1, SeriesItemType.NOTE, b2);

    List<SeriesActivity> active =
        seriesItemReader.activeSeries(2, 1000).stream()
            .filter(a -> a.seriesId().equals(seriesId) || a.seriesId().equals(otherSeriesId))
            .toList();

    assertThat(active).extracting(SeriesActivity::seriesId).containsExactly(seriesId);
    assertThat(active.get(0).itemCount()).isEqualTo(2);
    assertThat(active.get(0).lastActiveAt())
        .isCloseTo(
            noteRepository.findById(a2).orElseThrow().getCreatedAt(),
            within(1, ChronoUnit.SECONDS));
  }

  @Test
  void theSubscriptionFeedInterleavesSeriesNotesWithPostsNewestFirst() {
    Long older = post("sit701-older", true);
    ReflectionTestUtils.setField(
        postRepository.findById(older).orElseThrow(),
        "publishedAt",
        Instant.now().minus(1, ChronoUnit.DAYS));
    Long note = note("a series note", NoteVisibility.PUBLIC, null);
    Long hidden = note("followers only", NoteVisibility.PRIVATE, null);
    place(
        seriesId,
        SeriesItemType.POST,
        older,
        SeriesItemType.NOTE,
        note,
        SeriesItemType.NOTE,
        hidden);
    postRepository.flush();

    assertThat(followingFeedReader.page(List.of(), List.of(seriesId), List.of(), 0, 10))
        .containsExactly(
            new FollowingFeedRef(SeriesItemType.NOTE, note, seriesId),
            new FollowingFeedRef(SeriesItemType.POST, older, null));
    assertThat(followingFeedReader.page(List.of(), List.of(seriesId), List.of(), 1, 10))
        .containsExactly(new FollowingFeedRef(SeriesItemType.POST, older, null));
    assertThat(followingFeedReader.count(List.of(), List.of(seriesId), List.of())).isEqualTo(2);
    assertThat(seriesItemReader.feedNotes(List.of(note, hidden)))
        .containsOnlyKeys(note)
        .hasEntrySatisfying(
            note,
            n -> {
              assertThat(n.author().username()).isEqualTo("sit701");
              assertThat(n.seriesSlug()).isEqualTo("sit701-a");
              assertThat(n.excerpt()).isEqualTo("a series note");
            });
    assertThat(seriesItemReader.feedNotes(List.of())).isEmpty();
    assertThat(
            followingFeedReader.count(List.of(authorId), List.of(otherSeriesId), List.of("sit701")))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void deletingANoteOrAPostDropsOnlyItsPlace() {
    Long post = post("sit701-gone", true);
    Long note = note("short lived", NoteVisibility.PUBLIC, null);
    Long kept = note("kept", NoteVisibility.PUBLIC, null);
    seriesItemRepository.replace(
        seriesId,
        List.of(
            new SeriesItemEntity(seriesId, SeriesItemType.POST, post, 0),
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, note, 1),
            new SeriesItemEntity(seriesId, SeriesItemType.NOTE, kept, 2)));

    seriesItemCleaner.purgeForNote(note);
    seriesItemRepository.deleteByRef(SeriesItemType.POST, post);

    assertThat(itemsOf(seriesId)).containsExactly("NOTE:" + kept);
    assertThat(noteSeriesReader.containing(note)).isEmpty();

    seriesItemRepository.deleteBySeriesId(seriesId);
    assertThat(itemsOf(seriesId)).isEmpty();
  }
}
