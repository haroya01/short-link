package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 테스트 바깥 트랜잭션 없이 실제 유스케이스 커밋 후 응답과 저장값을 비교한다. */
@SpringBootTest
@ActiveProfiles("test")
class PostWriteViewCommitIntegrationTest {
  private static final Instant OLD_UPDATED_AT = Instant.parse("2000-01-01T00:00:00Z");

  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostRevisionRepository revisions;
  @Autowired private PostBlockRepository blocks;
  @Autowired private CreatePostUseCase createPost;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private SchedulePostUseCase schedulePost;
  @Autowired private UnpublishPostUseCase unpublishPost;
  @Autowired private RepublishPostUseCase republishPost;
  @Autowired private BackToDraftPostUseCase backToDraft;
  @Autowired private RestorePostRevisionUseCase restoreRevision;

  private TransactionTemplate transaction;
  private Long userId;
  private Long postId;

  @BeforeEach
  void createAuthor() {
    transaction = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    userId =
        transaction.execute(
            status ->
                users
                    .save(new UserEntity("write-view-" + unique + "@x.com", "google", unique))
                    .getId());
  }

  @AfterEach
  void cleanup() {
    transaction.executeWithoutResult(
        status -> {
          if (postId != null) {
            revisions.deleteAllByPostId(postId);
            blocks.deleteAllByPostId(postId);
            posts.findById(postId).ifPresent(posts::delete);
            posts.flush();
          }
          if (userId != null) users.deleteById(userId);
        });
  }

  @Test
  void creationReturnsGeneratedTimestampsAfterCommit() {
    PostView response =
        createPost.execute(new CreatePostCommand(userId, "new-post", "New post", "ko"));
    postId = response.id();

    assertStoredTimestamps(response);
    assertThat(response.tags()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(WriteOperation.class)
  void writeResponseContainsCommittedTimestampAndDetachedTags(WriteOperation operation) {
    postId =
        transaction.execute(
            status -> {
              PostEntity post = new PostEntity(userId, "write-view-post", "Write view", "ko");
              post.updateTags(List.of("spring", "jpa"));
              if (operation == WriteOperation.BACK_TO_DRAFT) {
                post.schedule(Instant.now().plusSeconds(3600));
              } else if (operation == WriteOperation.UNPUBLISH
                  || operation == WriteOperation.REPUBLISH) {
                post.publish();
                if (operation == WriteOperation.REPUBLISH) post.unpublish();
              }
              Long id = posts.save(post).getId();
              if (operation == WriteOperation.RESTORE) {
                revisions.save(
                    new PostRevisionEntity(
                        id,
                        1,
                        "Snapshot",
                        """
            {"title":"Snapshot","excerpt":null,"ogImageUrl":null,"ogImageKey":null,
             "languageTag":"ko","blocks":[]}
            """));
              }
              return id;
            });
    // 이전 응답 시각과 새 저장 시각을 확실히 구분한다. 벽시계 sleep에 의존하지 않는다.
    jdbc.update(
        "UPDATE posts SET updated_at = ? WHERE id = ?", Timestamp.from(OLD_UPDATED_AT), postId);

    PostView response = execute(operation);

    assertStoredTimestamps(response);
    assertThat(response.updatedAt()).isAfter(OLD_UPDATED_AT);
    assertThat(response.tags()).containsExactly("spring", "jpa");
  }

  private PostView execute(WriteOperation operation) {
    return switch (operation) {
      case METADATA ->
          updateMetadata.execute(
              new UpdatePostMetadataCommand(
                  userId,
                  postId,
                  null,
                  null,
                  null,
                  "https://cdn.example.com/new.png",
                  "new.png",
                  null,
                  null));
      case ADMIN_METADATA -> updateMetadata.adminExecute(userId, postId, null, null);
      case PUBLISH -> publishPost.execute(new PublishPostCommand(userId, postId));
      case SCHEDULE ->
          schedulePost.execute(
              new SchedulePostCommand(userId, postId, Instant.now().plusSeconds(3600)));
      case UNPUBLISH -> unpublishPost.execute(new UnpublishPostCommand(userId, postId));
      case REPUBLISH -> republishPost.execute(new RepublishPostCommand(userId, postId));
      case BACK_TO_DRAFT -> backToDraft.execute(new BackToDraftPostCommand(userId, postId));
      case RESTORE -> restoreRevision.execute(new RestorePostRevisionCommand(userId, postId, 1));
    };
  }

  private void assertStoredTimestamps(PostView response) {
    PostEntity stored = posts.findById(postId).orElseThrow();
    assertThat(response.createdAt()).isNotNull();
    assertThat(response.updatedAt()).isNotNull();
    // MySQL DATETIME(6)의 저장 정밀도로 비교한다.
    assertThat(response.createdAt().truncatedTo(ChronoUnit.MICROS))
        .isEqualTo(stored.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
    assertThat(response.updatedAt().truncatedTo(ChronoUnit.MICROS))
        .isEqualTo(stored.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
  }

  enum WriteOperation {
    METADATA,
    ADMIN_METADATA,
    PUBLISH,
    SCHEDULE,
    UNPUBLISH,
    REPUBLISH,
    BACK_TO_DRAFT,
    RESTORE
  }
}
