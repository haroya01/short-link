package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicTrendingSpreadIntegrationTest {

  private static final String LANGUAGE = "eo";

  @Autowired private PublicFeedQueryService service;
  @Autowired private PostRepository posts;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private String tag;

  @BeforeEach
  void tag() {
    tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  private long author(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private String publish(long userId, int readers, List<String> tags) {
    String slug = "s" + UUID.randomUUID().toString().substring(0, 8);
    PostEntity post = new PostEntity(userId, slug, slug, LANGUAGE);
    post.updateTags(tags);
    post.publish();
    DiscoverableBodies.discoverable(post);
    long id = posts.save(post).getId();
    for (int i = 0; i < readers; i++) {
      jdbc.update(
          "INSERT INTO post_view_event (post_id, viewed_at, is_bot, visitor_hash)"
              + " VALUES (?, NOW(6), FALSE, ?)",
          id,
          "v" + i);
    }
    return slug;
  }

  private List<String> slugs(PublicFeedQuery query) {
    return service.feed(null, query).items().stream().map(PublicFeedItem::slug).toList();
  }

  @Test
  void anAuthorPlacesTwoPostsPerRoundOnTrendingAndOnATagsTrending() {
    long prolific = author("p");
    long quiet = author("q");
    String a5 = publish(prolific, 5, List.of(tag));
    String a4 = publish(prolific, 4, List.of(tag));
    String a3 = publish(prolific, 3, List.of(tag));
    String a2 = publish(prolific, 2, List.of(tag));
    String b1 = publish(quiet, 1, List.of(tag));

    assertThat(slugs(PublicFeedQuery.from(null, null, "trending", LANGUAGE, 0, 20)))
        .containsSubsequence(a5, a4, b1, a3, a2);
    assertThat(slugs(PublicFeedQuery.from(null, tag, "trending", null, 0, 20)))
        .containsExactly(a5, a4, b1, a3, a2);
    assertThat(slugs(PublicFeedQuery.from(null, tag, "trending", null, 1, 2)))
        .containsExactly(b1, a3);
  }

  @Test
  void aTagMoreWritersUseRanksAboveOneWritersFavourite() {
    long prolific = author("p");
    long quiet = author("q");
    String shared = tag + "shared";
    String favourite = tag + "fav";
    publish(prolific, 0, List.of(favourite, shared));
    publish(prolific, 0, List.of(favourite));
    publish(prolific, 0, List.of(favourite));
    publish(quiet, 0, List.of(shared));

    List<String> ranked =
        service.popularTags(10_000).stream()
            .map(TagCount::tag)
            .filter(Set.of(shared, favourite)::contains)
            .toList();
    assertThat(ranked).containsExactly(shared, favourite);
  }
}
