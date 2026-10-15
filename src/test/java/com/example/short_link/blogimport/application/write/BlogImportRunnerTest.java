package com.example.short_link.blogimport.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.link.ShortLinkIssuer;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.post.application.image.PostImageService;
import com.example.short_link.post.application.write.BeginImportedPostCommand;
import com.example.short_link.post.application.write.ImportedPostWriter;
import com.example.short_link.post.application.write.PublishImportedPostCommand;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.SourceUrlHash;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class BlogImportRunnerTest {

  private static final Instant VERIFIED = Instant.parse("2026-10-15T00:00:00Z");
  private static final Long USER = 7L;
  private static final Long SOURCE = 3L;

  private final ImportSourceRepository sources = mock(ImportSourceRepository.class);
  private final BlogFeeds feeds = mock(BlogFeeds.class);
  private final HtmlBlockConverter html = new HtmlBlockConverter(JsonMapper.builder().build());
  private final ImportLedger ledger = mock(ImportLedger.class);
  private final ImportSourceRuns runs = mock(ImportSourceRuns.class);
  private final ImportedPostWriter posts = mock(ImportedPostWriter.class);
  private final PostImageService images = mock(PostImageService.class);
  private final ShortLinkIssuer shortLinks = mock(ShortLinkIssuer.class);
  private final NoteCommandService notes = mock(NoteCommandService.class);
  private final RedisDistributedLock lock = mock(RedisDistributedLock.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserEntity writer = new UserEntity("w@x.com", "google", "g-w");
  private final BlogImportRunner runner =
      new BlogImportRunner(
          sources,
          feeds,
          html,
          ledger,
          runs,
          posts,
          images,
          shortLinks,
          notes,
          lock,
          users,
          Clock.fixed(VERIFIED.plusSeconds(3600), ZoneOffset.UTC));
  private final AtomicLong ids = new AtomicLong(100);

  private ImportSourceEntity source;

  @BeforeEach
  void setUp() {
    source =
        new ImportSourceEntity(
            USER,
            BlogAddress.parse("velog.io/@writer").orElseThrow(),
            "writer.log",
            true,
            "kurl-X");
    ReflectionTestUtils.setField(source, "id", SOURCE);
    source.verify(VERIFIED, 0);
    when(sources.findById(SOURCE)).thenReturn(Optional.of(source));
    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    lenient().when(users.findById(USER)).thenReturn(Optional.of(writer));
    lenient()
        .when(runs.start(anyLong(), anyInt(), anyString(), any(Boolean.class)))
        .thenReturn(true);
    lenient().when(runs.progress(anyLong(), anyInt(), anyInt())).thenReturn(true);
    lenient().when(ledger.taken(eq(SOURCE), any())).thenReturn(Map.of());
    lenient().when(ledger.claim(eq(SOURCE), anyString())).thenAnswer(i -> ids.incrementAndGet());
    lenient().when(posts.begin(any())).thenAnswer(i -> Optional.of(ids.incrementAndGet()));
    lenient().when(posts.publish(any())).thenReturn(true);
    lenient()
        .when(shortLinks.issue(anyLong(), anyString()))
        .thenAnswer(
            i -> Optional.of(new ShortLinkIssuer.Issued(55L, "abc123", "https://kurl.me/abc123")));
    lenient()
        .when(images.importFromUrl(anyLong(), anyLong(), anyString()))
        .thenAnswer(
            i ->
                new PostImageService.CommitResult(
                    "https://img.kurl.me/" + Math.abs(((String) i.getArgument(2)).hashCode()),
                    "post-images/key"));
  }

  private static BlogFeed.Post post(String slug, Instant at, String html) {
    return new BlogFeed.Post("https://velog.io/@writer/" + slug, slug, at, html, List.of("tag"));
  }

  private void feed(boolean full, BlogFeed.Post... posts) {
    when(feeds.read(any()))
        .thenReturn(
            new BlogFeed(source.address(), "writer.log", "desc", "ko", full, List.of(posts)));
  }

  @Test
  void aFirstImportPublishesOldestFirstWithoutAnnouncing() {
    feed(
        true,
        post("new", VERIFIED.minusSeconds(10), "<p>new</p>"),
        post(
            "old",
            VERIFIED.minusSeconds(100),
            "<p>old</p><img src=\"https://cdn.example/a.png\">"));

    runner.run(SOURCE);

    ArgumentCaptor<BeginImportedPostCommand> begun =
        ArgumentCaptor.forClass(BeginImportedPostCommand.class);
    verify(posts, times(2)).begin(begun.capture());
    assertThat(begun.getAllValues())
        .extracting(BeginImportedPostCommand::sourceUrl)
        .containsExactly("https://velog.io/@writer/old", "https://velog.io/@writer/new");
    ArgumentCaptor<PublishImportedPostCommand> published =
        ArgumentCaptor.forClass(PublishImportedPostCommand.class);
    verify(posts, times(2)).publish(published.capture());
    PublishImportedPostCommand old = published.getAllValues().get(0);
    assertThat(old.excerptOnly()).isFalse();
    assertThat(old.coverUrl()).startsWith("https://img.kurl.me/");
    assertThat(old.coverChosen()).isFalse();
    assertThat(old.linkId()).isEqualTo(55L);
    assertThat(old.hiddenFromDiscover()).isFalse();
    assertThat(old.blocks())
        .extracting(b -> b.type())
        .containsExactly(PostBlockType.PARAGRAPH, PostBlockType.IMAGE);
    assertThat(old.blocks().get(1).content()).contains("https://img.kurl.me/");
    verify(runs).start(SOURCE, 2, "writer.log", true);
    verify(runs).progress(SOURCE, 2, 0);
    verify(runs).finish(SOURCE, "writer.log", true);
    verify(ledger, times(2)).complete(anyLong());
    verifyNoInteractions(notes);
  }

  @Test
  void aSyncAnnouncesOnlyPostsWrittenAfterVerificationWithoutMentioning() {
    source.finishRun(VERIFIED.plusSeconds(1));
    feed(
        true,
        post("fresh", VERIFIED.plusSeconds(60), "<p>@Transactional 이야기</p>"),
        post("backfill", VERIFIED.minusSeconds(60), "<p>old</p>"));

    runner.run(SOURCE);

    ArgumentCaptor<NoteDraft> note = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes, times(1)).create(eq(USER), note.capture());
    assertThat(note.getValue().body())
        .isEqualTo("fresh\n＠Transactional 이야기\nhttps://kurl.me/abc123");
    assertThat(note.getValue().visibility()).isEqualTo("public");
  }

  @Test
  void anAnnouncementFailureDoesNotFailTheImport() {
    source.finishRun(VERIFIED.plusSeconds(1));
    feed(true, post("fresh", VERIFIED.plusSeconds(60), "<p>x</p>"));
    when(notes.create(anyLong(), any())).thenThrow(new IllegalStateException("suspended"));
    when(shortLinks.issue(anyLong(), anyString())).thenReturn(Optional.empty());

    runner.run(SOURCE);

    ArgumentCaptor<NoteDraft> note = ArgumentCaptor.forClass(NoteDraft.class);
    verify(notes).create(eq(USER), note.capture());
    assertThat(note.getValue().body()).endsWith("\nhttps://velog.io/@writer/fresh");
    verify(runs).progress(SOURCE, 1, 0);
  }

  @Test
  void summaryFeedsKeepTheExcerptAndChooseTheThumbnailAsCover() {
    source.updateSettings(null, false);
    feed(
        false,
        post(
            "naver",
            VERIFIED.minusSeconds(5),
            "요약 # 문장....... <img src=\"https://blogthumb.example/t.png\">"));

    runner.run(SOURCE);

    ArgumentCaptor<PublishImportedPostCommand> published =
        ArgumentCaptor.forClass(PublishImportedPostCommand.class);
    verify(posts).publish(published.capture());
    PublishImportedPostCommand cmd = published.getValue();
    assertThat(cmd.excerptOnly()).isTrue();
    assertThat(cmd.coverChosen()).isTrue();
    assertThat(cmd.coverUrl()).startsWith("https://img.kurl.me/");
    assertThat(cmd.coverKey()).isEqualTo("post-images/key");
    assertThat(cmd.hiddenFromDiscover()).isTrue();
    assertThat(cmd.excerpt()).isEqualTo("요약 # 문장…");
    assertThat(cmd.blocks()).hasSize(1);
    assertThat(cmd.blocks().get(0).content()).isEqualTo("요약 # 문장…");
  }

  @Test
  void imagesThatCannotBeRehostedKeepTheirOriginalAddress() {
    feed(true, post("p", VERIFIED, "<p>x</p><img src=\"https://cdn.example/a.png\">"));
    when(images.importFromUrl(anyLong(), anyLong(), anyString()))
        .thenThrow(new IllegalStateException("storage off"));

    runner.run(SOURCE);

    ArgumentCaptor<PublishImportedPostCommand> published =
        ArgumentCaptor.forClass(PublishImportedPostCommand.class);
    verify(posts).publish(published.capture());
    assertThat(published.getValue().blocks().get(1).content())
        .contains("https://cdn.example/a.png");
    assertThat(published.getValue().coverUrl()).isEqualTo("https://cdn.example/a.png");
    assertThat(published.getValue().coverKey()).isNull();
  }

  @Test
  void onlyTheFirstThirtyImagesOfAPostAreRehosted() {
    StringBuilder many = new StringBuilder("<p>x</p>");
    for (int i = 0; i < 35; i++) {
      many.append("<img src=\"https://cdn.example/").append(i).append(".png\">");
    }
    feed(true, post("p", VERIFIED, many.toString()));

    runner.run(SOURCE);

    verify(images, times(BlogImportRunner.MAX_IMAGES_PER_POST))
        .importFromUrl(anyLong(), anyLong(), anyString());
  }

  @Test
  void settledOriginalsAreSkippedAndUnsettledOnesResumeTheirDraft() {
    BlogFeed.Post done = post("done", VERIFIED, "<p>d</p>");
    BlogFeed.Post retry = post("retry", VERIFIED, "<p>r</p>");
    feed(true, done, retry);
    ImportItemEntity settled = new ImportItemEntity(SOURCE, SourceUrlHash.of(done.url()));
    settled.complete(VERIFIED);
    ImportItemEntity unsettled = new ImportItemEntity(SOURCE, SourceUrlHash.of(retry.url()));
    ReflectionTestUtils.setField(unsettled, "id", 9L);
    unsettled.attachPost(42L);
    when(ledger.taken(eq(SOURCE), any()))
        .thenReturn(
            Map.of(
                SourceUrlHash.of(done.url()), settled, SourceUrlHash.of(retry.url()), unsettled));

    runner.run(SOURCE);

    ArgumentCaptor<BeginImportedPostCommand> begun =
        ArgumentCaptor.forClass(BeginImportedPostCommand.class);
    verify(posts).begin(begun.capture());
    assertThat(begun.getValue().sourceUrl()).isEqualTo(retry.url());
    assertThat(begun.getValue().resumePostId()).isEqualTo(42L);
    verify(ledger, never()).claim(anyLong(), anyString());
    verify(ledger).complete(9L);
  }

  @Test
  void failuresAreCountedAndRetriedLaterWhileTheRestGoesOn() {
    feed(
        true,
        post("a", VERIFIED.minusSeconds(3), "<p>a</p>"),
        post("b", VERIFIED.minusSeconds(2), "<p>b</p>"),
        post("c", VERIFIED.minusSeconds(1), "<p>c</p>"));
    when(posts.publish(any()))
        .thenReturn(true)
        .thenThrow(new IllegalStateException("db"))
        .thenReturn(false);

    runner.run(SOURCE);

    verify(runs).progress(SOURCE, 1, 0);
    verify(runs).progress(SOURCE, 1, 1);
    verify(runs).progress(SOURCE, 1, 2);
    verify(ledger, times(2)).fail(anyLong());
    verify(runs).finish(SOURCE, "writer.log", true);
  }

  @Test
  void anOriginalAnotherRunTookOrAlreadyPublishedIsSkipped() {
    feed(
        true,
        post("raced", VERIFIED.minusSeconds(2), "<p>a</p>"),
        post("published", VERIFIED.minusSeconds(1), "<p>b</p>"));
    when(ledger.claim(eq(SOURCE), anyString()))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenReturn(77L);
    when(posts.begin(any())).thenReturn(Optional.empty());

    runner.run(SOURCE);

    verify(posts, times(1)).begin(any());
    verify(ledger).complete(77L);
    verify(posts, never()).publish(any());
    verify(runs, times(2)).progress(SOURCE, 0, 0);
  }

  @Test
  void aFeedFailureMarksTheSourceAndImportsNothing() {
    when(feeds.read(any()))
        .thenThrow(new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, "x"));

    runner.run(SOURCE);

    verify(runs).fail(SOURCE, "IMPORT_SOURCE_UNREACHABLE");
    verifyNoInteractions(posts);
    verify(lock).release("kurl:blog-import:source:" + SOURCE);
  }

  @Test
  void nothingRunsWithoutTheLockOrAVerifiedSource() {
    when(lock.tryAcquire(anyString(), any())).thenReturn(false);
    runner.run(SOURCE);
    verifyNoInteractions(feeds);

    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    source.disconnect();
    runner.run(SOURCE);
    when(sources.findById(SOURCE)).thenReturn(Optional.empty());
    runner.run(SOURCE);
    verifyNoInteractions(feeds);
  }

  @Test
  void aSuspendedDeletedOrMissingWriterIsNotSynced() {
    writer.suspend(VERIFIED.plusSeconds(7200));
    runner.run(SOURCE);

    UserEntity deleted = new UserEntity("d@x.com", "google", "g-d");
    deleted.softDelete();
    when(users.findById(USER)).thenReturn(Optional.of(deleted));
    runner.run(SOURCE);

    when(users.findById(USER)).thenReturn(Optional.empty());
    runner.run(SOURCE);

    verifyNoInteractions(feeds, posts, ledger);
  }

  @Test
  void aSourceDisconnectedMidRunStops() {
    feed(
        true,
        post("a", VERIFIED.minusSeconds(2), "<p>a</p>"),
        post("b", VERIFIED.minusSeconds(1), "<p>b</p>"));
    when(runs.progress(anyLong(), anyInt(), anyInt())).thenReturn(false);

    runner.run(SOURCE);

    verify(posts, times(1)).publish(any());
    verify(runs, never()).finish(anyLong(), anyString(), any(Boolean.class));
  }

  @Test
  void aSourceThatStopsBeforeTheRunStartsImportsNothing() {
    feed(true, post("a", VERIFIED, "<p>a</p>"));
    when(runs.start(anyLong(), anyInt(), anyString(), any(Boolean.class))).thenReturn(false);

    runner.run(SOURCE);

    verifyNoInteractions(posts);
  }

  @Test
  void anEmptyFeedJustFinishesTheRun() {
    feed(true);

    runner.run(SOURCE);

    verify(runs, never()).start(anyLong(), anyInt(), anyString(), any(Boolean.class));
    verify(runs).finish(SOURCE, "writer.log", true);
  }

  @Test
  void aFullPostWithNothingToConvertFallsBackToItsSummary() {
    feed(true, post("empty", VERIFIED, "<iframe src=\"https://x.example\"></iframe>"));

    runner.run(SOURCE);

    ArgumentCaptor<PublishImportedPostCommand> published =
        ArgumentCaptor.forClass(PublishImportedPostCommand.class);
    verify(posts).publish(published.capture());
    assertThat(published.getValue().excerptOnly()).isTrue();
    assertThat(published.getValue().blocks()).isEmpty();
    assertThat(published.getValue().coverUrl()).isNull();
  }

  @Test
  void titlesFallBackToTheTextAndThenToAPlaceholder() {
    assertThat(BlogImportRunner.title("  제목  ", "본문")).isEqualTo("제목");
    assertThat(BlogImportRunner.title(" ", "본문 " + "가".repeat(100)))
        .endsWith("…")
        .hasSizeLessThanOrEqualTo(60);
    assertThat(BlogImportRunner.title(null, null)).isEqualTo("제목 없음");
    assertThat(BlogImportRunner.title("t".repeat(250), "")).hasSize(200);
  }

  @Test
  void mentionsAreDefused() {
    assertThat(BlogImportRunner.unmention("@velopert 님")).isEqualTo("＠velopert 님");
  }
}
