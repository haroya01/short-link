package com.example.short_link.blogimport.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.application.read.ImportPreviewView;
import com.example.short_link.blogimport.application.read.ImportSourceQueryService;
import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.ImportSourceStatus;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.write.ImportedPostWriter;
import com.example.short_link.post.domain.SourceUrlHash;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class ImportSourceUseCasesTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private static final Long USER = 7L;
  private static final Long OTHER = 8L;
  private static final BlogAddress VELOG = BlogAddress.parse("velog.io/@writer").orElseThrow();

  private final BlogFeeds feeds = mock(BlogFeeds.class);
  private final ImportSourceRepository sources = mock(ImportSourceRepository.class);
  private final ImportSourceQueryService views = mock(ImportSourceQueryService.class);
  private final UserModerationGuard moderation = mock(UserModerationGuard.class);
  private final ImportLedger ledger = mock(ImportLedger.class);
  private final ImportedPostWriter posts = mock(ImportedPostWriter.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private BlogFeed feed;

  @BeforeEach
  void setUp() {
    TransactionStatus status = new SimpleTransactionStatus();
    when(tx.getTransaction(any())).thenReturn(status);
    feed =
        new BlogFeed(
            VELOG,
            "writer.log",
            "about me kurl-ABCDEF",
            "ko",
            true,
            List.of(
                new BlogFeed.Post("https://velog.io/@writer/a", "a", NOW, "<p>a</p>", List.of()),
                new BlogFeed.Post("https://velog.io/@writer/b", "b", NOW, "<p>b</p>", List.of())));
    lenient().when(feeds.parse(anyString())).thenReturn(VELOG);
    lenient().when(feeds.read(any())).thenReturn(feed);
    lenient().when(views.viewOf(any())).thenAnswer(i -> ImportSourceView.of(i.getArgument(0), 0));
    lenient().when(sources.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(sources.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private static ImportSourceEntity source(
      Long owner, String code, Consumer<ImportSourceEntity> s) {
    ImportSourceEntity source = new ImportSourceEntity(owner, VELOG, "writer.log", true, code);
    ReflectionTestUtils.setField(source, "id", 3L);
    s.accept(source);
    return source;
  }

  private static BlogImportErrorCode codeOf(Runnable call) {
    try {
      call.run();
    } catch (BlogImportException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a BlogImportException");
  }

  private ConnectImportSourceUseCase connect() {
    return new ConnectImportSourceUseCase(feeds, sources, views, moderation, tx);
  }

  private VerifyImportSourceUseCase verifyUseCase() {
    return new VerifyImportSourceUseCase(feeds, sources, ledger, views, events, clock, tx);
  }

  @Test
  void connectingANewBlogHandsOutACode() {
    when(sources.findByUserIdAndBlogKey(USER, "velog:writer")).thenReturn(Optional.empty());

    ImportSourceView view = connect().execute(USER, "velog.io/@writer");

    verify(moderation).requireCanWrite(USER);
    assertThat(view.status()).isEqualTo("PENDING_VERIFICATION");
    assertThat(view.verificationCode()).matches("kurl-[2-9A-HJKMNP-Z]{6}");
    assertThat(view.blogTitle()).isEqualTo("writer.log");
    assertThat(view.fullContent()).isTrue();
    assertThat(view.announceNewPosts()).isTrue();
    assertThat(view.progress()).isNull();
  }

  @Test
  void connectingAgainKeepsTheCodeAndAReconnectGetsANewOne() {
    ImportSourceEntity pending = source(USER, "kurl-KEEPME", s -> {});
    when(sources.findByUserIdAndBlogKey(USER, "velog:writer")).thenReturn(Optional.of(pending));

    assertThat(connect().execute(USER, "velog.io/@writer").verificationCode())
        .isEqualTo("kurl-KEEPME");
    verify(sources, never()).saveAndFlush(any());

    pending.verify(NOW, 0);
    pending.disconnect();
    ImportSourceView reconnected = connect().execute(USER, "velog.io/@writer");
    assertThat(reconnected.status()).isEqualTo("PENDING_VERIFICATION");
    assertThat(reconnected.verificationCode()).isNotEqualTo("kurl-KEEPME").startsWith("kurl-");
  }

  @Test
  void aBlogAnotherMemberVerifiedCannotBeConnected() {
    when(sources.isClaimedByAnother("velog:writer", USER)).thenReturn(true);

    assertThat(codeOf(() -> connect().execute(USER, "velog.io/@writer")))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
  }

  @Test
  void connectionsAreCapped() {
    when(sources.findByUserIdAndBlogKey(USER, "velog:writer")).thenReturn(Optional.empty());
    when(sources.countActiveByUserId(USER))
        .thenReturn((long) ConnectImportSourceUseCase.MAX_SOURCES);

    assertThat(codeOf(() -> connect().execute(USER, "velog.io/@writer")))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_LIMIT);
  }

  @Test
  void aConcurrentConnectReturnsTheRowThatWon() {
    ImportSourceEntity winner = source(USER, "kurl-WINNER", s -> {});
    when(sources.findByUserIdAndBlogKey(USER, "velog:writer"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(sources.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThat(connect().execute(USER, "velog.io/@writer").verificationCode())
        .isEqualTo("kurl-WINNER");
  }

  @Test
  void aConcurrentConnectWithNoWinnerRethrows() {
    when(sources.findByUserIdAndBlogKey(USER, "velog:writer")).thenReturn(Optional.empty());
    when(sources.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> connect().execute(USER, "velog.io/@writer"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void verifyingWithTheCodeInTheDescriptionStartsTheFirstImport() {
    ImportSourceEntity pending = source(USER, "kurl-ABCDEF", s -> {});
    when(sources.findById(3L)).thenReturn(Optional.of(pending));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(pending));
    ImportItemEntity settled =
        new ImportItemEntity(3L, SourceUrlHash.of("https://velog.io/@writer/a"));
    settled.complete(NOW);
    when(ledger.taken(eq(3L), any()))
        .thenReturn(Map.of(SourceUrlHash.of("https://velog.io/@writer/a"), settled));

    ImportSourceView view = verifyUseCase().execute(USER, 3L);

    assertThat(view.status()).isEqualTo("IMPORTING");
    assertThat(view.verificationCode()).isNull();
    assertThat(view.progress()).isEqualTo(new ImportSourceView.Progress(1, 0, 0));
    assertThat(pending.getClaimKey()).isEqualTo("velog:writer");
    assertThat(pending.getVerifiedAt()).isEqualTo(NOW);
    verify(events).publishEvent(new ImportSourceVerified(3L));
  }

  @Test
  void verifyingWithoutTheCodeIsRefused() {
    ImportSourceEntity pending = source(USER, "kurl-ZZZZZZ", s -> {});
    when(sources.findById(3L)).thenReturn(Optional.of(pending));

    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING);
    verify(events, never()).publishEvent(any());
  }

  @Test
  void verifyingSomeoneElsesSourceIsNotFound() {
    when(sources.findById(3L)).thenReturn(Optional.of(source(OTHER, "kurl-ABCDEF", s -> {})));

    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND);
    when(sources.findById(4L)).thenReturn(Optional.empty());
    assertThat(codeOf(() -> verifyUseCase().execute(USER, 4L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND);
  }

  @Test
  void verifyingAVerifiedSourceJustShowsIt() {
    ImportSourceEntity verified = source(USER, "kurl-ABCDEF", s -> s.verify(NOW, 0));
    when(sources.findById(3L)).thenReturn(Optional.of(verified));

    assertThat(verifyUseCase().execute(USER, 3L).status()).isEqualTo("IMPORTING");
    verify(feeds, never()).read(any());
  }

  @Test
  void aDisconnectedSourceHasNoCodeToVerify() {
    ImportSourceEntity gone =
        source(
            USER,
            "kurl-ABCDEF",
            s -> {
              s.verify(NOW, 0);
              s.disconnect();
            });
    when(sources.findById(3L)).thenReturn(Optional.of(gone));

    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING);
  }

  @Test
  void aRaceForTheSameBlogEndsAsTaken() {
    ImportSourceEntity pending = source(USER, "kurl-ABCDEF", s -> {});
    when(sources.findById(3L)).thenReturn(Optional.of(pending));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(pending));
    when(ledger.taken(eq(3L), any())).thenReturn(Map.of());
    when(sources.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("claim"));

    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
  }

  @Test
  void aClaimSeenUnderTheLockIsTaken() {
    ImportSourceEntity pending = source(USER, "kurl-ABCDEF", s -> {});
    when(sources.findById(3L)).thenReturn(Optional.of(pending));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(pending));
    when(sources.isClaimedByAnother("velog:writer", USER)).thenReturn(true);

    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
  }

  @Test
  void aSourceVerifiedOrRecodedMeanwhileIsLeftAlone() {
    ImportSourceEntity pending = source(USER, "kurl-ABCDEF", s -> {});
    ImportSourceEntity verifiedMeanwhile = source(USER, "kurl-ABCDEF", s -> s.verify(NOW, 0));
    when(sources.findById(3L)).thenReturn(Optional.of(pending));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(verifiedMeanwhile));
    assertThat(verifyUseCase().execute(USER, 3L).status()).isEqualTo("IMPORTING");

    ImportSourceEntity recoded = source(USER, "kurl-OTHER2", s -> {});
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(recoded));
    assertThat(codeOf(() -> verifyUseCase().execute(USER, 3L)))
        .isEqualTo(BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING);
    verify(events, never()).publishEvent(any());
  }

  @Test
  void hidingASourceFromDiscoverHidesItsPostsOnce() {
    ImportSourceEntity source = source(USER, "kurl-ABCDEF", s -> s.verify(NOW, 0));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(source));
    UpdateImportSourceUseCase update = new UpdateImportSourceUseCase(sources, posts, views);

    ImportSourceView hidden = update.execute(USER, 3L, null, false);
    assertThat(hidden.showInDiscover()).isFalse();
    verify(posts).hideFromDiscover(3L, true);

    update.execute(USER, 3L, false, null);
    verify(posts).hideFromDiscover(eq(3L), any(Boolean.class));
    assertThat(source.isAnnounceNewPosts()).isFalse();

    assertThat(codeOf(() -> update.execute(OTHER, 3L, true, true)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND);
  }

  @Test
  void disconnectingKeepsPostsUnlessAskedToDeleteThem() {
    ImportSourceEntity source = source(USER, "kurl-ABCDEF", s -> s.verify(NOW, 0));
    when(sources.findByIdForUpdate(3L)).thenReturn(Optional.of(source));
    DisconnectImportSourceUseCase disconnect =
        new DisconnectImportSourceUseCase(sources, posts, tx);

    disconnect.execute(USER, 3L, true);
    assertThat(source.getStatus()).isEqualTo(ImportSourceStatus.DISCONNECTED);
    verify(posts, never()).deleteAllFrom(anyLong(), anyLong());
    verify(sources, never()).delete(any());

    disconnect.execute(USER, 3L, false);
    verify(posts).deleteAllFrom(USER, 3L);
    verify(sources).delete(source);

    assertThat(codeOf(() -> disconnect.execute(OTHER, 3L, true)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND);
  }

  @Test
  void aPreviewListsThePostsWithExcerptsAndThumbnails() {
    PreviewBlogUseCase preview =
        new PreviewBlogUseCase(feeds, new HtmlBlockConverter(JsonMapper.builder().build()));
    when(feeds.read(any()))
        .thenReturn(
            new BlogFeed(
                VELOG,
                "writer.log",
                "d",
                "ko",
                false,
                List.of(
                    new BlogFeed.Post(
                        "https://velog.io/@writer/a",
                        " ",
                        NOW,
                        "요약입니다 <img src=\"https://cdn.example/t.png\">",
                        List.of()))));

    ImportPreviewView view = preview.execute("velog.io/@writer");

    assertThat(view.platform()).isEqualTo("VELOG");
    assertThat(view.blogUrl()).isEqualTo("https://velog.io/@writer");
    assertThat(view.fullContent()).isFalse();
    assertThat(view.items()).hasSize(1);
    ImportPreviewView.Item item = view.items().get(0);
    assertThat(item.title()).isEqualTo("요약입니다");
    assertThat(item.excerpt()).isEqualTo("요약입니다");
    assertThat(item.imageUrl()).isEqualTo("https://cdn.example/t.png");
    assertThat(item.publishedAt()).isEqualTo(NOW);
  }

  @Test
  void aSyncRunsEveryDueSourceAndSurvivesOneFailing() {
    BlogImportRunner runner = mock(BlogImportRunner.class);
    RedisDistributedLock lock = mock(RedisDistributedLock.class);
    when(lock.tryAcquire(anyString(), any())).thenReturn(true);
    when(sources.findDueIds(any(), any(), any(Integer.class))).thenReturn(List.of(1L, 2L, 3L));
    doThrow(new IllegalStateException("boom")).when(runner).run(2L);
    SyncImportSourcesUseCase sync = new SyncImportSourcesUseCase(sources, runner, lock, clock);

    sync.execute();

    verify(runner).run(1L);
    verify(runner).run(3L);
    ArgumentCaptor<Instant> syncedBefore = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> stalledBefore = ArgumentCaptor.forClass(Instant.class);
    verify(sources)
        .findDueIds(
            syncedBefore.capture(), stalledBefore.capture(), eq(SyncImportSourcesUseCase.BATCH));
    assertThat(syncedBefore.getValue())
        .isEqualTo(NOW.minus(SyncImportSourcesUseCase.SYNC_INTERVAL));
    assertThat(stalledBefore.getValue())
        .isEqualTo(NOW.minus(SyncImportSourcesUseCase.STALLED_AFTER));
    verify(lock).release(SyncImportSourcesUseCase.LOCK_KEY);
    assertThat(sync.runDue()).isEqualTo(2);
  }

  @Test
  void aSyncAlreadyRunningElsewhereIsSkipped() {
    BlogImportRunner runner = mock(BlogImportRunner.class);
    RedisDistributedLock lock = mock(RedisDistributedLock.class);
    when(lock.tryAcquire(anyString(), any())).thenReturn(false);

    new SyncImportSourcesUseCase(sources, runner, lock, clock).execute();

    verify(sources, never()).findDueIds(any(), any(), any(Integer.class));
    verify(lock, never()).release(anyString());
  }

  @Test
  void codesAreKurlPrefixedAndUnambiguous() {
    for (int i = 0; i < 50; i++) {
      assertThat(VerificationCodes.next()).matches("kurl-[2-9A-HJKMNP-Z]{6}");
    }
    assertThat(VerificationCodes.next()).isNotEqualTo(VerificationCodes.next());
  }

  @Test
  void sourceRunsOnlyTouchVerifiedSources() {
    ImportSourceRuns runs = new ImportSourceRuns(sources, clock);
    ImportSourceEntity pending = source(USER, "kurl-ABCDEF", s -> {});
    ImportSourceEntity verified = source(USER, "kurl-ABCDEF", s -> s.verify(NOW, 0));
    when(sources.findById(1L)).thenReturn(Optional.of(pending));
    when(sources.findById(2L)).thenReturn(Optional.of(verified));
    when(sources.findById(9L)).thenReturn(Optional.empty());

    assertThat(runs.start(1L, 5, "t", true)).isFalse();
    assertThat(runs.progress(1L, 1, 0)).isFalse();
    assertThat(runs.start(9L, 5, "t", true)).isFalse();
    assertThat(runs.progress(9L, 1, 0)).isFalse();
    assertThat(runs.start(2L, 5, "new title", false)).isTrue();
    assertThat(runs.progress(2L, 4, 1)).isTrue();
    assertThat(verified.getProgressDone()).isEqualTo(4);
    assertThat(verified.getBlogTitle()).isEqualTo("new title");

    runs.fail(2L, "IMPORT_FEED_NOT_FOUND");
    assertThat(verified.getStatus()).isEqualTo(ImportSourceStatus.ERROR);
    runs.finish(2L, "final", true);
    assertThat(verified.getStatus()).isEqualTo(ImportSourceStatus.SYNCING);
    assertThat(verified.getLastSyncedAt()).isEqualTo(NOW);
    assertThat(verified.getBlogTitle()).isEqualTo("final");
    runs.finish(1L, "ignored", true);
    assertThat(pending.getBlogTitle()).isEqualTo("writer.log");
  }
}
