package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PostEditConflictIntegrationTest {
  @Autowired private ReplacePostBlocksUseCase replaceBlocks;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
  @Autowired private RestorePostRevisionUseCase restoreRevision;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private UnpublishPostUseCase unpublishPost;
  @Autowired private RepublishPostUseCase republishPost;
  @Autowired private SchedulePostUseCase schedulePost;
  @Autowired private PublishScheduledPostUseCase publishScheduledPost;
  @Autowired private SetPinnedPostsUseCase setPinnedPosts;
  @Autowired private IssuePreviewTokenUseCase issuePreview;
  @Autowired private UserRepository users;
  @Autowired private PostBlockRepository blocks;
  @Autowired private PostRevisionRepository revisions;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private PostRepository posts;
  @MockitoSpyBean private PostSearchTextUpdater searchText;

  private TransactionTemplate transactions;
  private Long userId;
  private Long postId;

  @BeforeEach
  void createAuthorAndDraft() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    userId =
        transactions.execute(
            status ->
                users
                    .save(new UserEntity("edit-conflict-" + unique + "@x.com", "google", unique))
                    .getId());
    postId =
        transactions.execute(
            status -> posts.save(new PostEntity(userId, "edit-conflict", "Draft", "ko")).getId());
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          revisions.deleteAllByPostId(postId);
          blocks.deleteAllByPostId(postId);
          jdbc.update("delete from post_search_text where post_id = ?", postId);
          posts.findById(postId).ifPresent(posts::delete);
          posts.flush();
          users.deleteById(userId);
        });
  }

  @ParameterizedTest
  @EnumSource(Edit.class)
  void twoSavesFromTheSameVersionLetExactlyOneThrough(Edit edit) throws Exception {
    CountDownLatch bothAskedForTheRow = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              bothAskedForTheRow.countDown();
              return invocation.callRealMethod();
            })
        .when(posts)
        .findByIdForUpdate(postId);
    // 먼저 잠근 쪽이 다른 쪽이 같은 행을 요청할 때까지 커밋하지 않아, 두 저장이 실제로 겹친다.
    doAnswer(
            invocation -> {
              assertThat(bothAskedForTheRow.await(10, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when(searchText)
        .refresh(any());
    doAnswer(
            invocation -> {
              assertThat(bothAskedForTheRow.await(10, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when(searchText)
        .refresh(any(), any());

    List<Attempt> attempts = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> save(edit, "first", 0L));
      var second = executor.submit(() -> save(edit, "second", 0L));
      attempts.add(first.get(30, TimeUnit.SECONDS));
      attempts.add(second.get(30, TimeUnit.SECONDS));
    }

    List<Attempt> saved = attempts.stream().filter(a -> a.conflict() == null).toList();
    List<Attempt> refused = attempts.stream().filter(a -> a.conflict() != null).toList();
    assertThat(saved).hasSize(1);
    assertThat(saved.getFirst().version()).isEqualTo(1L);
    assertThat(refused).hasSize(1);
    assertThat(refused.getFirst().conflict().errorCode())
        .isEqualTo(PostErrorCode.POST_EDIT_CONFLICT);
    assertThat(refused.getFirst().conflict().properties()).containsEntry("contentVersion", 1L);
    assertThat(storedVersion()).isEqualTo(1L);
    assertThat(storedText(edit)).isEqualTo(saved.getFirst().text());
  }

  @Test
  void savesWithoutABaseVersionStillOverwriteAndAdvance() {
    save(Edit.BODY, "mine", 0L);

    Attempt legacy = save(Edit.BODY, "legacy", null);

    assertThat(legacy.conflict()).isNull();
    assertThat(storedText(Edit.BODY)).isEqualTo("legacy");
    assertThat(storedVersion()).isEqualTo(2L);
  }

  @Test
  void overwrittenBodyAndTitleComeBackFromHistory() {
    save(Edit.BODY, "their body", 0L);
    save(Edit.METADATA, "Their title", 1L);

    assertThat(save(Edit.BODY, "my body", 0L).conflict()).isNotNull();
    PostBlockView overwritten =
        replaceBlocks
            .execute(
                new ReplacePostBlocksCommand(
                    userId,
                    postId,
                    List.of(
                        new ReplacePostBlocksCommand.BlockInput(
                            PostBlockType.PARAGRAPH, "my body")),
                    0L,
                    true))
            .blocks()
            .getFirst();

    assertThat(overwritten.content()).isEqualTo("my body");
    assertThat(storedVersion()).isEqualTo(3L);
    PostRevisionEntity kept = revisions.findLatestByPostId(postId).orElseThrow();
    assertThat(kept.getTitleSnapshot()).isEqualTo("Their title");
    assertThat(kept.getContentJson()).contains("their body").doesNotContain("my body");

    var restored =
        restoreRevision.execute(
            new RestorePostRevisionCommand(userId, postId, kept.getVersionNumber()));

    assertThat(restored.title()).isEqualTo("Their title");
    assertThat(restored.contentVersion()).isEqualTo(4L);
    assertThat(storedText(Edit.BODY)).isEqualTo("their body");
    assertThat(storedVersion()).isEqualTo(4L);
  }

  @Test
  void lifecycleWritesBetweenSavesDoNotRefuseTheEditorsNextSave() {
    Attempt titled = save(Edit.METADATA, "Ready", 0L);
    long version = titled.version();

    publishPost.execute(new PublishPostCommand(userId, postId));
    setPinnedPosts.execute(userId, List.of(postId));
    issuePreview.issue(userId, postId);
    unpublishPost.execute(new UnpublishPostCommand(userId, postId));
    republishPost.execute(new RepublishPostCommand(userId, postId));
    assertThat(storedVersion()).isEqualTo(version);

    Attempt afterPublish = save(Edit.BODY, "after publish", version);

    assertThat(afterPublish.conflict()).isNull();
    assertThat(afterPublish.version()).isEqualTo(version + 1);
  }

  @Test
  void scheduledPublicationDoesNotRefuseTheEditorsNextSave() {
    Attempt titled = save(Edit.METADATA, "Later", 0L);
    schedulePost.execute(new SchedulePostCommand(userId, postId, Instant.now().plusSeconds(3600)));

    assertThat(publishScheduledPost.execute(postId, Instant.now().plusSeconds(7200))).isTrue();
    assertThat(storedVersion()).isEqualTo(titled.version());

    assertThat(save(Edit.BODY, "after the job", titled.version()).conflict()).isNull();
  }

  private Attempt save(Edit edit, String text, Long baseVersion) {
    try {
      long version =
          switch (edit) {
            case BODY ->
                replaceBlocks
                    .execute(
                        new ReplacePostBlocksCommand(
                            userId,
                            postId,
                            List.of(
                                new ReplacePostBlocksCommand.BlockInput(
                                    PostBlockType.PARAGRAPH, text)),
                            baseVersion,
                            false))
                    .contentVersion();
            case METADATA ->
                updateMetadata
                    .execute(
                        new UpdatePostMetadataCommand(
                            userId,
                            postId,
                            text,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            baseVersion,
                            false))
                    .contentVersion();
          };
      return new Attempt(text, version, null);
    } catch (PostException conflict) {
      return new Attempt(text, null, conflict);
    }
  }

  private long storedVersion() {
    return jdbc.queryForObject(
        "select content_version from posts where id = ?", Long.class, postId);
  }

  private String storedText(Edit edit) {
    return switch (edit) {
      case BODY ->
          jdbc.queryForObject(
              "select content from post_block where post_id = ?", String.class, postId);
      case METADATA ->
          jdbc.queryForObject("select title from posts where id = ?", String.class, postId);
    };
  }

  enum Edit {
    BODY,
    METADATA
  }

  private record Attempt(String text, Long version, PostException conflict) {}
}
