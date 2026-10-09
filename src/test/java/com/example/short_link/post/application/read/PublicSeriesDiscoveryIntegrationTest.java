package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// The series discovery query groups published posts by series, drops thin series via HAVING, and
// orders by the latest member's publish time — only the real GROUP BY / HAVING / MAX against MySQL
// (and the Instant mapping it returns) proves it, so this drives it end-to-end through the service.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicSeriesDiscoveryIntegrationTest {

  @Autowired private PublicSeriesQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private SeriesItemRepository seriesItemRepository;

  private long author(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    userRepository.save(u);
    return u.getId();
  }

  private long createSeries(long userId, String slug, String title) {
    return seriesRepository.save(new SeriesEntity(userId, slug, title)).getId();
  }

  private long publishInSeries(long userId, String slug, long seriesId, int order) {
    PostEntity p = new PostEntity(userId, slug, slug, "ko");
    p.assignToSeries(seriesId, order);
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  // The series' order lives in series_item; the write use case keeps it beside posts.series_id.
  private void order(long seriesId, long... postIds) {
    List<SeriesItemEntity> rows = new ArrayList<>();
    for (long postId : postIds) {
      rows.add(new SeriesItemEntity(seriesId, SeriesItemType.POST, postId, rows.size()));
    }
    seriesItemRepository.replace(seriesId, rows);
  }

  @Test
  void discoversSeriesWithAtLeastTwoPublishedMembers() {
    long a = author("seriesauthor");

    long deep = createSeries(a, "deep-dive", "Deep Dive");
    long dd1 = publishInSeries(a, "dd-1", deep, 0);
    long dd2 = publishInSeries(a, "dd-2", deep, 1);
    PostEntity draft = new PostEntity(a, "dd-3-draft", "dd3", "ko");
    draft.assignToSeries(deep, 2);
    long dd3 = postRepository.save(draft).getId();
    order(deep, dd1, dd2, dd3);

    long thin = createSeries(a, "thin", "Thin");
    order(thin, publishInSeries(a, "thin-1", thin, 0));

    List<PublicSeriesCard> cards = service.discoverSeries(10);

    assertThat(cards)
        .extracting(PublicSeriesCard::slug)
        .contains("deep-dive")
        .doesNotContain("thin");
    PublicSeriesCard dd =
        cards.stream().filter(c -> c.slug().equals("deep-dive")).findFirst().orElseThrow();
    assertThat(dd.postCount()).isEqualTo(2);
    assertThat(dd.lastPublishedAt()).isNotNull();
    assertThat(dd.author().username()).isEqualTo("seriesauthor");
    assertThat(dd.posts()).extracting(SeriesPostRef::slug).containsExactly("dd-1", "dd-2");
  }
}
