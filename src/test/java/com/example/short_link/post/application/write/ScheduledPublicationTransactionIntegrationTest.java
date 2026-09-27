package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(ScheduledPublicationTransactionIntegrationTest.EventConfiguration.class)
class ScheduledPublicationTransactionIntegrationTest {
  @Autowired private PublishScheduledPostsUseCase publishScheduledPosts;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private PublishScheduledPostUseCase publishScheduledPost;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
  @Autowired private ReplacePostBlocksUseCase replaceBlocks;
  @Autowired private RestorePostRevisionUseCase restoreRevision;
  @Autowired private IssuePreviewTokenUseCase issuePreview;
  @Autowired private UserRepository users;
  @Autowired private PostRevisionRepository revisions;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CommittedPublications publications;
  @MockitoSpyBean private PostRepository posts;
  @MockitoSpyBean private PostSearchTextUpdater searchText;

  private final List<Long> postIds = new ArrayList<>();
  private TransactionTemplate transactions;
  private Long userId;

  @BeforeEach
  void createAuthor() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    userId =
        transactions.execute(
            status ->
                users
                    .save(new UserEntity("schedule-" + unique + "@x.com", "google", unique))
                    .getId());
    publications.events.clear();
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          for (Long postId : postIds) {
            revisions.deleteAllByPostId(postId);
            jdbc.update("delete from post_search_text where post_id = ?", postId);
            posts.findById(postId).ifPresent(posts::delete);
          }
          posts.flush();
          users.deleteById(userId);
        });
  }

  @Test
  void failedSearchWriteRollsBackOnlyItsPostAndTheNextPostStillCommits() {
    Instant now = Instant.now().plusSeconds(7200);
    Long failedId = scheduledPost("failed");
    Long successfulId = scheduledPost("successful");
    doAnswer(
            invocation -> {
              PostEntity post = invocation.getArgument(0);
              if (post.getId().equals(failedId))
                throw new IllegalStateException("search write failed");
              return invocation.callRealMethod();
            })
        .when(searchText)
        .refresh(any());

    assertThat(publishScheduledPosts.execute(now)).isEqualTo(1);

    assertThat(posts.findById(failedId).orElseThrow().isScheduled()).isTrue();
    assertThat(posts.findById(failedId).orElseThrow().getPublishedAt()).isNull();
    assertThat(posts.findById(successfulId).orElseThrow().isPublished()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from post_revision where post_id = ?", Long.class, failedId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from post_revision where post_id = ?", Long.class, successfulId))
        .isEqualTo(1L);
    assertThat(publications.events)
        .extracting(PostPublishedEvent::postId)
        .containsExactly(successfulId);
  }

  @Test
  void concurrentSchedulersSelectingTheSameCandidatePublishAndNotifyOnce() throws Exception {
    Instant now = Instant.now().plusSeconds(7200);
    Long postId = scheduledPost("concurrent");
    CountDownLatch selected = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              Object candidates = invocation.callRealMethod();
              selected.countDown();
              assertThat(selected.await(10, TimeUnit.SECONDS)).isTrue();
              return candidates;
            })
        .when(posts)
        .findScheduledDueIds(now);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> publishScheduledPosts.execute(now));
      var second = executor.submit(() -> publishScheduledPosts.execute(now));
      assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
    }

    assertThat(posts.findById(postId).orElseThrow().isPublished()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from post_revision where post_id = ?", Long.class, postId))
        .isEqualTo(1L);
    assertThat(publications.events).extracting(PostPublishedEvent::postId).containsExactly(postId);
  }

  @Test
  void manualPublishAndScheduledPublishShareTheFirstPublicationDecision() throws Exception {
    Instant now = Instant.now().plusSeconds(7200);
    Long postId = scheduledPost("manual-race");
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var manual =
          executor.submit(
              () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return publishPost.execute(new PublishPostCommand(userId, postId));
              });
      var scheduled =
          executor.submit(
              () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return publishScheduledPost.execute(postId, now);
              });
      start.countDown();
      assertThat(manual.get(20, TimeUnit.SECONDS).status()).isEqualTo("PUBLISHED");
      scheduled.get(20, TimeUnit.SECONDS);
    }

    assertThat(publications.events).extracting(PostPublishedEvent::postId).containsExactly(postId);
  }

  @ParameterizedTest
  @EnumSource(EditOperation.class)
  void editingWhilePublicationCommitsCannotRestoreTheOldScheduledState(EditOperation operation)
      throws Exception {
    Instant now = Instant.now().plusSeconds(7200);
    Long postId = scheduledPost("publication-edit");
    if (operation == EditOperation.RESTORE) {
      transactions.executeWithoutResult(
          status ->
              revisions.save(
                  new PostRevisionEntity(
                      postId,
                      1,
                      "Restored",
                      "{\"title\":\"Restored\",\"excerpt\":null,\"ogImageUrl\":null,\"ogImageKey\":null,\"languageTag\":\"ko\",\"blocks\":[]}")));
    }
    CountDownLatch publicationLocked = new CountDownLatch(1);
    CountDownLatch editQueryStarted = new CountDownLatch(1);
    CountDownLatch publicationFinished = new CountDownLatch(1);
    AtomicReference<Thread> editor = new AtomicReference<>();
    doAnswer(
            invocation -> {
              PostEntity post = invocation.getArgument(0);
              if (post.getId().equals(postId) && Thread.currentThread() != editor.get()) {
                publicationLocked.countDown();
                assertThat(editQueryStarted.await(10, TimeUnit.SECONDS)).isTrue();
              }
              return invocation.callRealMethod();
            })
        .when(searchText)
        .refresh(any());
    doAnswer(
            invocation -> {
              if (Thread.currentThread() == editor.get()) editQueryStarted.countDown();
              return invocation.callRealMethod();
            })
        .when(posts)
        .findByIdForUpdate(postId);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              if (Thread.currentThread() == editor.get()) {
                // 잠금 없는 조회가 발행 전 상태를 읽고 발행 후 덮어쓰는 경쟁을 재현한다.
                editQueryStarted.countDown();
                assertThat(publicationFinished.await(10, TimeUnit.SECONDS)).isTrue();
              }
              return result;
            })
        .when(posts)
        .findById(postId);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var publication =
          executor.submit(
              () -> {
                try {
                  return publishScheduledPost.execute(postId, now);
                } finally {
                  publicationFinished.countDown();
                }
              });
      assertThat(publicationLocked.await(10, TimeUnit.SECONDS)).isTrue();
      var edit =
          executor.submit(
              () -> {
                editor.set(Thread.currentThread());
                applyEdit(operation, postId);
              });
      assertThat(publication.get(20, TimeUnit.SECONDS)).isTrue();
      edit.get(20, TimeUnit.SECONDS);
    }

    PostEntity stored = posts.findById(postId).orElseThrow();
    assertThat(stored.isPublished()).isTrue();
    assertThat(stored.getPublishedAt()).isNotNull();
    assertThat(stored.getScheduledAt()).isNull();
    assertThat(publications.events).extracting(PostPublishedEvent::postId).containsExactly(postId);
  }

  private void applyEdit(EditOperation operation, Long postId) {
    switch (operation) {
      case METADATA ->
          assertThat(
                  updateMetadata
                      .execute(
                          new UpdatePostMetadataCommand(
                              userId, postId, "Edited", null, null, null, null, null, null))
                      .title())
              .isEqualTo("Edited");
      case ADMIN_METADATA ->
          assertThat(updateMetadata.adminExecute(userId, postId, "Edited", null).title())
              .isEqualTo("Edited");
      case BODY ->
          assertThat(replaceBlocks.execute(new ReplacePostBlocksCommand(userId, postId, List.of())))
              .isEmpty();
      case RESTORE ->
          assertThat(
                  restoreRevision
                      .execute(new RestorePostRevisionCommand(userId, postId, 1))
                      .title())
              .isEqualTo("Restored");
      case PREVIEW -> assertThat(issuePreview.issue(userId, postId)).isNotBlank();
    }
  }

  enum EditOperation {
    METADATA,
    ADMIN_METADATA,
    BODY,
    RESTORE,
    PREVIEW
  }

  private Long scheduledPost(String slug) {
    Long id =
        transactions.execute(
            status -> {
              PostEntity post = new PostEntity(userId, slug, "Scheduled " + slug, "ko");
              post.schedule(Instant.now().plusSeconds(3600));
              return posts.save(post).getId();
            });
    postIds.add(id);
    return id;
  }

  @TestConfiguration
  static class EventConfiguration {
    @Bean
    CommittedPublications committedPublications() {
      return new CommittedPublications();
    }
  }

  static class CommittedPublications {
    final ConcurrentLinkedQueue<PostPublishedEvent> events = new ConcurrentLinkedQueue<>();

    @TransactionalEventListener
    public void onPublication(PostPublishedEvent event) {
      events.add(event);
    }
  }
}
