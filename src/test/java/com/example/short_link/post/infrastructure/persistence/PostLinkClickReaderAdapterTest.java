package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLinkClick;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostLinkClickReaderAdapterTest {

  @Autowired private PostLinkClickReader reader;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void linkClicksFromAPostLeaveBotsOut() {
    UserEntity author = users.save(new UserEntity("post-clicks@example.com", "google", "g-plc"));
    PostEntity post = posts.save(new PostEntity(author.getId(), "bot-free", "Bot free", "ko"));
    LinkEntity link =
        links.save(new LinkEntity("https://example.com/cited", "plc0001", author.getId(), null));
    click(link, post, false);
    click(link, post, false);
    click(link, post, true);
    Instant since = Instant.now().minus(1, ChronoUnit.HOURS);

    assertThat(reader.countByPostId(post.getId())).isEqualTo(2);
    assertThat(reader.countByPostIdSince(post.getId(), since)).isEqualTo(2);
    assertThat(reader.countByUserId(author.getId())).isEqualTo(2);
    assertThat(reader.countByUserIdSince(author.getId(), since)).isEqualTo(2);
    assertThat(reader.breakdownByPostId(post.getId(), 10))
        .containsExactly(new PostLinkClick("plc0001", "https://example.com/cited", 2));
  }

  private void click(LinkEntity link, PostEntity post, boolean bot) {
    jdbc.update(
        "INSERT INTO click_event (link_id, post_id, clicked_at, is_bot) "
            + "VALUES (?, ?, FROM_UNIXTIME(?), ?)",
        link.getId(),
        post.getId(),
        Instant.now().getEpochSecond(),
        bot);
  }
}
