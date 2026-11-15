package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.write.CreateCommentCommand;
import com.example.short_link.post.application.write.CreateCommentUseCase;
import com.example.short_link.post.application.write.CreateHighlightReplyCommand;
import com.example.short_link.post.application.write.CreateHighlightReplyUseCase;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class MentionNoticeIntegrationTest {

  @Autowired private CreateCommentUseCase createComment;
  @Autowired private CreateHighlightReplyUseCase createHighlightReply;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostHighlightRepository highlights;
  @Autowired private JdbcTemplate jdbc;

  private long author;
  private long writer;
  private long mentioned;
  private String mentionedHandle;
  private long postId;

  @BeforeEach
  void aPublishedPostAndThreeMembers() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    author = member("mna" + suffix);
    writer = member("mnw" + suffix);
    mentionedHandle = "mnm" + suffix;
    mentioned = member(mentionedHandle);
    PostEntity post = new PostEntity(author, "mention-" + suffix, "A post worth discussing", "ko");
    post.publish();
    postId = posts.save(post).getId();
  }

  @AfterEach
  void removeRows() {
    jdbc.update(
        "DELETE FROM notification WHERE recipient_user_id IN (?, ?, ?)", author, writer, mentioned);
    jdbc.update(
        "DELETE FROM highlight_reply WHERE highlight_id IN "
            + "(SELECT id FROM post_highlight WHERE post_id = ?)",
        postId);
    jdbc.update("DELETE FROM post_highlight WHERE post_id = ?", postId);
    jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
    jdbc.update("DELETE FROM posts WHERE id = ?", postId);
    jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", author, writer, mentioned);
  }

  @Test
  void aMemberMentionedInACommentIsToldAfterTheCommentCommits() {
    createComment.execute(
        new CreateCommentCommand(writer, postId, null, "what do you think, @" + mentionedHandle),
        PostViewer.of(writer));

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(mentions()).isEqualTo(1));
  }

  @Test
  void aMemberMentionedUnderAHighlightIsToldAfterTheReplyCommits() {
    long highlightId =
        highlights.save(new PostHighlightEntity(postId, author, 0, 0, 0, 5, "quote", null)).getId();

    createHighlightReply.execute(
        new CreateHighlightReplyCommand(writer, highlightId, "@" + mentionedHandle + " look here"),
        PostViewer.of(writer));

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(mentions()).isEqualTo(1));
  }

  private long member(String handle) {
    UserEntity user = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long mentions() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? AND type = ?",
        Long.class,
        mentioned,
        "MENTION");
  }
}
