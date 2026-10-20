package com.example.short_link.search.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.write.PostSearchTextFlattener;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.search.domain.RelatedTag;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

// Not @Transactional: InnoDB FULLTEXT does not see rows a MATCH's own transaction inserted.
@SpringBootTest
@ActiveProfiles("test")
class RelatedSearchIntegrationTest {

  @Autowired private RelatedSearchQueryService related;
  @Autowired private PostRepository posts;
  @Autowired private PostSearchTextRepository searchTexts;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final PostSearchTextFlattener flattener =
      new PostSearchTextFlattener(JsonMapper.builder().build());
  private final List<Long> people = new ArrayList<>();
  private final List<Long> written = new ArrayList<>();
  private final List<Long> noted = new ArrayList<>();
  private String word;

  @BeforeEach
  void word() {
    word = "rq" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  @AfterEach
  void cleanUp() {
    noted.forEach(id -> jdbc.update("DELETE FROM note WHERE id = ?", id));
    written.forEach(id -> jdbc.update("DELETE FROM posts WHERE id = ?", id));
    people.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
  }

  private long person(String prefix) {
    String name = prefix + word;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    long id = users.save(user).getId();
    people.add(id);
    return id;
  }

  private void post(long author, String title, List<String> tags) {
    PostEntity post =
        new PostEntity(author, "s" + UUID.randomUUID().toString().substring(0, 8), title, "ko");
    post.updateTags(tags);
    post.publish();
    DiscoverableBodies.discoverable(post);
    long id = posts.save(post).getId();
    written.add(id);
    searchTexts.upsert(id, flattener.flatten(title, null, tags, List.of()));
  }

  private void note(long author, String body, String... tags) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility) VALUES (?, ?, NOW(6), 'PUBLIC')",
        author,
        body);
    Long id = jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author);
    noted.add(id);
    for (String tag : tags) {
      jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, ?)", id, tag);
    }
  }

  @Test
  void tagsWornWithTheWordsRankByHowManyPeopleUseThemAndLeaveOutTheWordsThemselves() {
    long mina = person("m");
    long yuki = person("y");
    long sora = person("s");
    String spring = "Spring" + word;
    String jpa = "JPA" + word;
    post(mina, "Java " + word + " 트랜잭션", List.of("Java", jpa));
    post(mina, "Java " + word + " 프록시", List.of("Java", jpa));
    post(mina, "Java " + word + " 영속성", List.of(jpa));
    post(mina, "Java " + word + " 빈", List.of("Java", spring));
    note(yuki, "#Java " + word + " 메모", "Java", spring);
    post(sora, "도커 " + word + " 네트워크", List.of("Docker" + word));

    List<RelatedTag> tags = related.related("#Java " + word, 8).tags();

    assertThat(tags).containsExactly(new RelatedTag(spring, 1, 1), new RelatedTag(jpa, 3, 0));
  }

  @Test
  void aQueryTheIndexCannotHoldHasNoRelatedTags() {
    assertThat(related.related("AI", 8).tags()).isEmpty();
    assertThat(related.related("  ", 8).tags()).isEmpty();
  }
}
