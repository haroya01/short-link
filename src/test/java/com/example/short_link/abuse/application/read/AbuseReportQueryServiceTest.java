package com.example.short_link.abuse.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.application.read.AbuseReportView.SubjectSnapshot;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader;
import com.example.short_link.post.application.read.PostPublicUrlBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AbuseReportQueryServiceTest {

  private static final long MISSING = 99L;

  private final AbuseReportRepository reports = mock(AbuseReportRepository.class);
  private final Subjects subjects = new Subjects();
  private final AbuseReportQueryService service =
      new AbuseReportQueryService(reports, subjects, new PostPublicUrlBuilder("https://kurl.me"));

  static Stream<Arguments> subjectRows() {
    return Stream.of(
        Arguments.of(
            AbuseSubjectType.POST,
            1L,
            new SubjectSnapshot("Hello", "alice", "https://kurl.me/p/alice/hello", null, false)),
        Arguments.of(
            AbuseSubjectType.POST,
            2L,
            new SubjectSnapshot("Gone", "alice", "https://kurl.me/p/alice/gone", null, true)),
        Arguments.of(AbuseSubjectType.POST, MISSING, SubjectSnapshot.EMPTY),
        Arguments.of(
            AbuseSubjectType.COMMENT, 10L, new SubjectSnapshot(null, "bob", null, "nice", false)),
        Arguments.of(
            AbuseSubjectType.COMMENT, 11L, new SubjectSnapshot(null, "bob", null, "kept", false)),
        Arguments.of(
            AbuseSubjectType.COMMENT, 12L, new SubjectSnapshot(null, "bob", null, "gone", true)),
        Arguments.of(AbuseSubjectType.COMMENT, MISSING, SubjectSnapshot.EMPTY),
        Arguments.of(
            AbuseSubjectType.HIGHLIGHT_REPLY,
            20L,
            new SubjectSnapshot(null, "carol", null, "quote", true)),
        Arguments.of(AbuseSubjectType.HIGHLIGHT_REPLY, MISSING, SubjectSnapshot.EMPTY),
        Arguments.of(
            AbuseSubjectType.USER, 30L, new SubjectSnapshot(null, "dave", null, null, false)),
        Arguments.of(
            AbuseSubjectType.USER, 31L, new SubjectSnapshot(null, "eve", null, null, true)),
        Arguments.of(AbuseSubjectType.USER, MISSING, SubjectSnapshot.EMPTY),
        Arguments.of(
            AbuseSubjectType.LINK,
            40L,
            new SubjectSnapshot("abc123", "frank", null, "https://spam.example", false)),
        Arguments.of(
            AbuseSubjectType.LINK,
            41L,
            new SubjectSnapshot("abc124", "frank", null, "https://spam.example", false)),
        Arguments.of(
            AbuseSubjectType.LINK,
            42L,
            new SubjectSnapshot("abc125", "frank", null, "https://spam.example", true)),
        Arguments.of(AbuseSubjectType.LINK, MISSING, SubjectSnapshot.EMPTY),
        Arguments.of(
            AbuseSubjectType.NOTE, 50L, new SubjectSnapshot(null, "gina", null, "hi", false)),
        Arguments.of(
            AbuseSubjectType.NOTE, MISSING, new SubjectSnapshot(null, null, null, null, true)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("subjectRows")
  void eachReportShowsWhatItsSubjectLooksLikeNow(
      AbuseSubjectType type, long subjectId, SubjectSnapshot expected) {
    AbuseReportView view = service.enrich(report(type, subjectId));

    assertThat(
            new SubjectSnapshot(
                view.subjectTitle(),
                view.subjectAuthorHandle(),
                view.subjectUrl(),
                view.subjectExcerpt(),
                view.subjectRemoved()))
        .isEqualTo(expected);
  }

  @Test
  void aListLooksUpEachKindOnceWithDistinctIdsAndSkipsKindsNobodyReported() {
    when(reports.findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus.OPEN))
        .thenReturn(
            List.of(
                report(AbuseSubjectType.POST, 1L),
                report(AbuseSubjectType.LINK, 40L),
                report(AbuseSubjectType.POST, 1L),
                report(AbuseSubjectType.POST, 2L)));

    List<AbuseReportView> views = service.listByStatus(AbuseReportStatus.OPEN);

    assertThat(views)
        .extracting(AbuseReportView::subjectTitle)
        .containsExactly("Hello", "abc123", "Hello", "Gone");
    assertThat(subjects.lookups)
        .isEqualTo(
            Map.of(AbuseSubjectType.POST, List.of(1L, 2L), AbuseSubjectType.LINK, List.of(40L)));
  }

  @Test
  void theFullListIsEnrichedLikeAFilteredOne() {
    when(reports.findAllByOrderByCreatedAtDesc())
        .thenReturn(
            List.of(report(AbuseSubjectType.USER, 31L), report(AbuseSubjectType.NOTE, MISSING)));

    assertThat(service.listAll())
        .extracting(AbuseReportView::subjectAuthorHandle, AbuseReportView::subjectRemoved)
        .containsExactly(tuple("eve", true), tuple(null, true));
  }

  @Test
  void anEmptyListLooksNothingUp() {
    when(reports.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

    assertThat(service.listAll()).isEmpty();
    assertThat(subjects.lookups).isEmpty();
  }

  private static AbuseReportEntity report(AbuseSubjectType type, long subjectId) {
    return new AbuseReportEntity(7L, type, subjectId, AbuseReason.SPAM, null);
  }

  record Post(
      Long getSubjectId, String getTitle, String getSlug, String getStatus, String getAuthorHandle)
      implements AbuseSubjectReader.PostSubjectSnapshot {}

  record Comment(Long getSubjectId, String getExcerpt, String getAuthorHandle, Long getDeleted)
      implements AbuseSubjectReader.CommentSubjectSnapshot {}

  record Note(Long getSubjectId, String getExcerpt, String getAuthorHandle)
      implements AbuseSubjectReader.NoteSubjectSnapshot {}

  record Link(
      Long getSubjectId,
      String getShortCode,
      String getOriginalUrl,
      String getOwnerHandle,
      Long getDisabled)
      implements AbuseSubjectReader.LinkSubjectSnapshot {}

  record User(Long getSubjectId, String getHandle, String getModerationStatus)
      implements AbuseSubjectReader.UserSubjectSnapshot {}

  static final class Subjects implements AbuseSubjectReader {

    final Map<AbuseSubjectType, List<Long>> lookups = new EnumMap<>(AbuseSubjectType.class);

    private final List<Post> posts =
        List.of(
            new Post(1L, "Hello", "hello", "PUBLISHED", "alice"),
            new Post(2L, "Gone", "gone", "UNPUBLISHED", "alice"));
    private final List<Comment> comments =
        List.of(
            new Comment(10L, "nice", "bob", null),
            new Comment(11L, "kept", "bob", 0L),
            new Comment(12L, "gone", "bob", 1L));
    private final List<Comment> highlightReplies = List.of(new Comment(20L, "quote", "carol", 1L));
    private final List<User> users =
        List.of(new User(30L, "dave", "ACTIVE"), new User(31L, "eve", "SUSPENDED"));
    private final List<Link> links =
        List.of(
            new Link(40L, "abc123", "https://spam.example", "frank", null),
            new Link(41L, "abc124", "https://spam.example", "frank", 0L),
            new Link(42L, "abc125", "https://spam.example", "frank", 1L));
    private final List<Note> notes = List.of(new Note(50L, "hi", "gina"));

    private <T> List<T> lookup(
        AbuseSubjectType type, Collection<Long> ids, List<T> rows, Function<T, Long> id) {
      if (ids.isEmpty()) {
        throw new IllegalArgumentException("IN () is not valid SQL");
      }
      lookups.put(type, new ArrayList<>(ids));
      return rows.stream().filter(r -> ids.contains(id.apply(r))).toList();
    }

    @Override
    public List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(lookup(AbuseSubjectType.POST, ids, posts, Post::getSubjectId));
    }

    @Override
    public List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(lookup(AbuseSubjectType.COMMENT, ids, comments, Comment::getSubjectId));
    }

    @Override
    public List<CommentSubjectSnapshot> findHighlightReplySubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(
          lookup(AbuseSubjectType.HIGHLIGHT_REPLY, ids, highlightReplies, Comment::getSubjectId));
    }

    @Override
    public List<UserSubjectSnapshot> findUserSubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(lookup(AbuseSubjectType.USER, ids, users, User::getSubjectId));
    }

    @Override
    public List<LinkSubjectSnapshot> findLinkSubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(lookup(AbuseSubjectType.LINK, ids, links, Link::getSubjectId));
    }

    @Override
    public List<NoteSubjectSnapshot> findNoteSubjectSnapshots(Collection<Long> ids) {
      return List.copyOf(lookup(AbuseSubjectType.NOTE, ids, notes, Note::getSubjectId));
    }

    @Override
    public Optional<Long> findLinkIdByShortCode(String shortCode) {
      return Optional.empty();
    }

    @Override
    public boolean subjectExists(AbuseSubjectType subjectType, Long subjectId) {
      return false;
    }
  }
}
