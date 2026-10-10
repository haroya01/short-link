package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
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
class ReservedPostSlugIntegrationTest {
  @Autowired private CreatePostUseCase createPost;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
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
                    .save(new UserEntity("reserved-" + unique + "@x.com", "google", unique))
                    .getId());
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              "delete from post_search_text where post_id in (select id from posts where user_id = ?)",
              authorId);
          posts.findAllByUserIdOrderByCreatedAtDesc(authorId).forEach(posts::delete);
          posts.flush();
          users.deleteById(authorId);
        });
  }

  @Test
  void aNewPostNamedAfterAProfilePageGetsNumberedPastTheAuthorsOwnPosts() {
    assertThat(createPost.execute(new CreatePostCommand(authorId, "notes", "", "ko")).slug())
        .isEqualTo("notes-2");
    assertThat(createPost.execute(new CreatePostCommand(authorId, "notes", "", "ko")).slug())
        .isEqualTo("notes-3");
  }

  @Test
  void aTypedProfilePageNameIsRefusedAndTheOldAddressStays() {
    Long postId = createPost.execute(new CreatePostCommand(authorId, "my-post", "", "ko")).id();

    assertThatThrownBy(
            () ->
                updateMetadata.execute(
                    new UpdatePostMetadataCommand(
                        authorId, postId, null, "series", null, null, null, null, null, null, null,
                        false)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.SLUG_RESERVED));
    assertThat(jdbc.queryForObject("select slug from posts where id = ?", String.class, postId))
        .isEqualTo("my-post");
  }
}
