package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.common.cache.WebCacheRevalidator;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.application.write.DeletePostCommand;
import com.example.short_link.post.application.write.DeletePostUseCase;
import com.example.short_link.post.application.write.PublishPostCommand;
import com.example.short_link.post.application.write.PublishPostUseCase;
import com.example.short_link.post.application.write.RecordPostViewCommand;
import com.example.short_link.post.application.write.RecordPostViewUseCase;
import com.example.short_link.post.application.write.ReplacePostBlocksCommand;
import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import com.example.short_link.post.application.write.ReplacePostBlocksUseCase;
import com.example.short_link.post.application.write.UnpublishPostCommand;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import com.example.short_link.post.application.write.UpdatePostMetadataCommand;
import com.example.short_link.post.application.write.UpdatePostMetadataUseCase;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PublishedPostChangeListenerIntegrationTest {

  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private CreatePostUseCase createPost;
  @Autowired private ReplacePostBlocksUseCase replaceBlocks;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private UnpublishPostUseCase unpublishPost;
  @Autowired private DeletePostUseCase deletePost;
  @Autowired private RecordPostViewUseCase recordView;

  @MockitoBean private WebCacheRevalidator revalidator;

  private TransactionTemplate transaction;
  private Long userId;
  private String username;

  @BeforeEach
  void author() {
    transaction = new TransactionTemplate(transactionManager);
    username = "wr" + UUID.randomUUID().toString().substring(0, 8);
    userId =
        transaction.execute(
            status -> {
              UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + username);
              user.claimUsername(username);
              return users.save(user).getId();
            });
  }

  @AfterEach
  void removeWhatTheTestCommitted() {
    String ofAuthor = " WHERE post_id IN (SELECT id FROM posts WHERE user_id = ?)";
    jdbc.update("DELETE FROM post_view_event" + ofAuthor, userId);
    jdbc.update("DELETE FROM post_revision" + ofAuthor, userId);
    jdbc.update("DELETE FROM post_block" + ofAuthor, userId);
    jdbc.update("DELETE FROM posts WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private Long draft(String slug) {
    return createPost.execute(new CreatePostCommand(userId, slug, "초안", "ko")).id();
  }

  private Long published(String slug) {
    Long id =
        transaction.execute(
            status -> {
              PostEntity post = new PostEntity(userId, slug, "공개 글", "ko");
              post.publish();
              return posts.save(post).getId();
            });
    verify(revalidator).requestRevalidation();
    clearInvocations(revalidator);
    return id;
  }

  private String committedStatus(Long postId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement("SELECT status FROM posts WHERE id = ?")) {
      statement.setLong(1, postId);
      try (var rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : "GONE";
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void writingSavingAndDeletingADraftNeverAsksTheWeb() {
    Long postId = draft("draft-only");
    replaceBlocks.execute(
        new ReplacePostBlocksCommand(
            userId, postId, List.of(new BlockInput(PostBlockType.PARAGRAPH, "본문")), 0L, false));
    updateMetadata.execute(
        new UpdatePostMetadataCommand(
            userId, postId, "고친 제목", null, null, null, null, null, null, null, 1L, false));
    deletePost.execute(new DeletePostCommand(userId, postId));

    verifyNoInteractions(revalidator);
  }

  @Test
  void publishingADraftAsksOnceAfterTheCommitIsVisible() {
    Long postId = draft("goes-public");
    List<String> seenByTheWeb = new ArrayList<>();
    doAnswer(invocation -> seenByTheWeb.add(committedStatus(postId)))
        .when(revalidator)
        .requestRevalidation();

    publishPost.execute(new PublishPostCommand(userId, postId));

    verify(revalidator).requestRevalidation();
    assertThat(seenByTheWeb).containsExactly("PUBLISHED");
  }

  @Test
  void editingThePublishedBodyAsks() {
    Long postId = published("edited");

    replaceBlocks.execute(
        new ReplacePostBlocksCommand(
            userId, postId, List.of(new BlockInput(PostBlockType.PARAGRAPH, "고친 본문")), 0L, false));

    verify(revalidator).requestRevalidation();
  }

  @Test
  void readersCountedOnAPublishedPostDoNotAsk() {
    Long postId = published("being-read");

    recordView.execute(
        new RecordPostViewCommand(username, "being-read"),
        VisitRequest.empty(),
        PostViewer.of(null));
    transaction.executeWithoutResult(
        status -> {
          PostEntity post = posts.findByIdForUpdate(postId).orElseThrow();
          post.incrementViewCount();
          post.countReader();
          posts.save(post);
        });

    verifyNoInteractions(revalidator);
    assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, postId))
        .isEqualTo(2L);
  }

  @Test
  void unpublishingAPublishedPostAsksOnce() {
    Long postId = published("taken-back");

    unpublishPost.execute(new UnpublishPostCommand(userId, postId));

    verify(revalidator).requestRevalidation();
  }

  @Test
  void deletingAPublishedPostAsksOnceAfterTheRowIsGone() {
    Long postId = published("deleted");
    List<String> seenByTheWeb = new ArrayList<>();
    doAnswer(invocation -> seenByTheWeb.add(committedStatus(postId)))
        .when(revalidator)
        .requestRevalidation();

    deletePost.execute(new DeletePostCommand(userId, postId));

    verify(revalidator).requestRevalidation();
    assertThat(seenByTheWeb).containsExactly("GONE");
  }

  @Test
  void aPublishThatRollsBackAsksNothing() {
    Long postId = draft("rolled-back");

    transaction.executeWithoutResult(
        status -> {
          publishPost.execute(new PublishPostCommand(userId, postId));
          status.setRollbackOnly();
        });

    verifyNoInteractions(revalidator);
    assertThat(committedStatus(postId)).isEqualTo("DRAFT");
  }
}
