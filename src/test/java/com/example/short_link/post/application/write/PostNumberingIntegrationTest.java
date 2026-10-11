package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PostNumberingIntegrationTest {
  @Autowired private CreatePostUseCase createPost;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private SchedulePostUseCase schedulePost;
  @Autowired private UnpublishPostUseCase unpublishPost;
  @Autowired private RepublishPostUseCase republishPost;
  @Autowired private DeletePostUseCase deletePost;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private TransactionTemplate transactions;
  private Long authorId;

  @BeforeEach
  void createAuthor() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    authorId =
        transactions.execute(
            status ->
                users
                    .save(new UserEntity("number-" + unique + "@x.com", "google", unique))
                    .getId());
  }

  @AfterEach
  void cleanup() {
    posts
        .findAllByUserIdOrderByCreatedAtDesc(authorId)
        .forEach(p -> deletePost.execute(new DeletePostCommand(authorId, p.getId())));
    transactions.executeWithoutResult(
        status -> {
          jdbc.update("delete from author_post_number where user_id = ?", authorId);
          users.deleteById(authorId);
        });
  }

  private Long draft(String slug) {
    return createPost.execute(new CreatePostCommand(authorId, slug, "제목 " + slug, "ko")).id();
  }

  private String publish(Long postId) {
    return publishPost.execute(new PublishPostCommand(authorId, postId)).slug();
  }

  @Test
  void numbersStartAtOneSkipATypedNumberAndAreNeverHandedOutAgain() {
    assertThat(publish(draft("draft-aaaaaaa"))).isEqualTo("1");
    assertThat(publish(draft("2"))).isEqualTo("2");
    Long scheduled = draft("p-1790819294-318");
    assertThat(
            schedulePost
                .execute(
                    new SchedulePostCommand(
                        authorId, scheduled, Instant.now().plus(2, ChronoUnit.HOURS)))
                .slug())
        .isEqualTo("3");
    Long fourth = draft("draft-bbbbbbb");
    assertThat(publish(fourth)).isEqualTo("4");

    deletePost.execute(new DeletePostCommand(authorId, fourth));
    assertThat(publish(draft("draft-ccccccc"))).isEqualTo("5");
    assertThat(publish(draft("my-own-address"))).isEqualTo("my-own-address");
  }

  @Test
  void unpublishingAndRepublishingKeepsTheNumber() {
    Long postId = draft("draft-ddddddd");
    assertThat(publish(postId)).isEqualTo("1");

    unpublishPost.execute(new UnpublishPostCommand(authorId, postId));
    assertThat(republishPost.execute(new RepublishPostCommand(authorId, postId)).slug())
        .isEqualTo("1");
  }

  @Test
  void twoPostsPublishedAtOnceGetTwoNumbers() throws Exception {
    Long first = draft("draft-eeeeeee");
    Long second = draft("draft-fffffff");
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<String> a =
          executor.submit(
              () -> {
                start.await();
                return publish(first);
              });
      Future<String> b =
          executor.submit(
              () -> {
                start.await();
                return publish(second);
              });
      start.countDown();
      assertThat(Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("1", "2");
    }
  }
}
