package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.ShortLinkApplication;
import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = ShortLinkApplication.class)
@ActiveProfiles("test")
class PostCoverChosenBackfillIntegrationTest {
  @Autowired private CreatePostUseCase createPost;
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
                users.save(new UserEntity("cover-" + unique + "@x.com", "google", unique)).getId());
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              "delete from post_block where post_id in (select id from posts where user_id = ?)",
              authorId);
          jdbc.update(
              "delete from post_search_text where post_id in (select id from posts where user_id = ?)",
              authorId);
          posts.findAllByUserIdOrderByCreatedAtDesc(authorId).forEach(posts::delete);
          posts.flush();
          users.deleteById(authorId);
        });
  }

  private Long post(String slug, String cover, String... blocks) {
    Long id = createPost.execute(new CreatePostCommand(authorId, slug, "", "ko")).id();
    jdbc.update("update posts set og_image_url = ?, cover_chosen = false where id = ?", cover, id);
    for (int i = 0; i < blocks.length; i += 2) {
      jdbc.update(
          "insert into post_block (post_id, block_type, content, block_order, created_at, updated_at)"
              + " values (?, ?, ?, ?, now(6), now(6))",
          id,
          blocks[i],
          blocks[i + 1],
          i / 2);
    }
    return id;
  }

  private boolean chosen(Long postId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("select cover_chosen from posts where id = ?", Boolean.class, postId));
  }

  @Test
  void aCoverThatIsTheBodysFirstImageCountsAsFilledInAndAnyOtherCoverAsChosen() {
    Long firstImageBlock =
        post(
            "first-image-block",
            "https://cdn/a.png",
            "PARAGRAPH",
            "글",
            "IMAGE",
            "{\"url\":\"https://cdn/a.png\",\"alt\":\"\"}");
    Long pastedInline =
        post(
            "pasted-inline",
            "https://cdn/b.png",
            "PARAGRAPH",
            "**목차**",
            "PARAGRAPH",
            "![«546x588» image.png](https://cdn/b.png)DATA_IO module은 …");
    Long codeComesFirst =
        post(
            "code-comes-first",
            "https://cdn/c.png",
            "CODE",
            "{\"code\":\"![x](https://cdn/not-an-image.png)\"}",
            "IMAGE",
            "{\"url\":\"https://cdn/c.png\"}");
    Long uploaded =
        post("uploaded", "https://cdn/uploaded.png", "IMAGE", "{\"url\":\"https://cdn/d.png\"}");
    Long laterImage =
        post(
            "later-image",
            "https://cdn/f.png",
            "IMAGE",
            "{\"url\":\"https://cdn/e.png\"}",
            "IMAGE",
            "{\"url\":\"https://cdn/f.png\"}");
    Long noBodyImage = post("no-body-image", "https://cdn/g.png", "PARAGRAPH", "글만 있다");

    jdbc.execute(
        (ConnectionCallback<Void>)
            conn -> {
              try {
                V182__post_cover_chosen.backfill(conn);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              return null;
            });

    assertThat(chosen(firstImageBlock)).isFalse();
    assertThat(chosen(pastedInline)).isFalse();
    assertThat(chosen(codeComesFirst)).isFalse();
    assertThat(chosen(uploaded)).isTrue();
    assertThat(chosen(laterImage)).isTrue();
    assertThat(chosen(noBodyImage)).isTrue();
  }
}
