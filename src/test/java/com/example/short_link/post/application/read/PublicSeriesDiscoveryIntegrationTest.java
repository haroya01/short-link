package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The series discovery query groups published posts by series, drops thin series via HAVING, and
 * orders by the latest member's publish time — only the real GROUP BY / HAVING / MAX against MySQL
 * (and the Instant mapping it returns) proves it, so this drives it end-to-end through the service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicSeriesDiscoveryIntegrationTest {

  @Autowired private PublicSeriesQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private UserRepository userRepository;

  private long author(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    userRepository.save(u);
    return u.getId();
  }

  private long createSeries(long userId, String slug, String title) {
    return seriesRepository.save(new SeriesEntity(userId, slug, title)).getId();
  }

  private void publishInSeries(long userId, String slug, long seriesId, int order) {
    PostEntity p = new PostEntity(userId, slug, slug, "ko");
    p.assignToSeries(seriesId, order);
    p.publish();
    postRepository.save(p);
  }

  @Test
  void discoversSeriesWithAtLeastTwoPublishedMembers() {
    long a = author("seriesauthor");

    long deep = createSeries(a, "deep-dive", "Deep Dive");
    publishInSeries(a, "dd-1", deep, 0);
    publishInSeries(a, "dd-2", deep, 1);
    // A draft member must not count toward the published total.
    PostEntity draft = new PostEntity(a, "dd-3-draft", "dd3", "ko");
    draft.assignToSeries(deep, 2);
    postRepository.save(draft);

    // Only one published member → below MIN_POSTS, must be excluded.
    long thin = createSeries(a, "thin", "Thin");
    publishInSeries(a, "thin-1", thin, 0);

    List<PublicSeriesCard> cards = service.discoverSeries(10);

    assertThat(cards)
        .extracting(PublicSeriesCard::slug)
        .contains("deep-dive")
        .doesNotContain("thin");
    PublicSeriesCard dd =
        cards.stream().filter(c -> c.slug().equals("deep-dive")).findFirst().orElseThrow();
    assertThat(dd.postCount()).isEqualTo(2); // draft excluded
    assertThat(dd.lastPublishedAt()).isNotNull();
    assertThat(dd.author().username()).isEqualTo("seriesauthor");
  }
}
