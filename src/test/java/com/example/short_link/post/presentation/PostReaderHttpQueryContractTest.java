package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostReaderHttpQueryContractTest extends ContentHttpJourneySupport {

  private static final String BROWSER =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/130.0.0.0 Safari/537.36";

  @Autowired private PostRepository posts;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void aSignedInReadersFirstVisitGivesTheAuthorAFirstReader() throws Exception {
    long postId =
        new TransactionTemplate(transactionManager)
            .execute(
                transaction -> {
                  PostEntity post =
                      new PostEntity(author.id(), "first-reader", "Waiting for a reader", "ko");
                  DiscoverableBodies.discoverable(post);
                  post.publish();
                  return posts.save(post).getId();
                });

    rawStep(
        "reader-post-view-beacon-human",
        "POST",
        publicPostPath("first-reader") + "/view",
        reader,
        null,
        202,
        Map.of("User-Agent", BROWSER, "Accept-Language", "ko"));

    assertThat(
            count(
                "post_view_event",
                "post_id = ? AND is_bot = FALSE AND visitor_hash IS NOT NULL",
                postId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT human_reader_count FROM posts WHERE id = ?", Integer.class, postId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT milestone FROM post_read_milestone WHERE post_id = ?",
                Integer.class,
                postId))
        .containsExactly(1);
    assertThat(
            count(
                "notification",
                "recipient_user_id = ? AND type = 'POST_FIRST_READER'",
                author.id()))
        .isEqualTo(1);
    verifyContracts();
  }
}
