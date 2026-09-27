package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.FeedSeriesRef;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class PublicFeedSeriesCollapseHttpTest extends DockerHttpTest {

  private static final Instant BASE = Instant.parse("2026-07-01T00:00:00Z");

  @LocalServerPort private int port;
  @Autowired private PostRepository posts;
  @Autowired private SeriesRepository seriesRepository;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final List<Long> postIds = new ArrayList<>();
  private Long seriesId;
  private Long authorId;
  private Long latestEpisodeId;
  private Long standaloneId;

  @BeforeEach
  void commitSeriesOfThreePublishedEpisodesAndOneStandalonePost() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction -> {
              UserEntity author = new UserEntity("series@example.com", "google", "series-author");
              author.claimUsername("series-author");
              users.save(author);
              authorId = author.getId();

              SeriesEntity series = new SeriesEntity(author.getId(), "rtt", "RTT 개선 기록");
              seriesRepository.save(series);
              seriesId = series.getId();

              for (int i = 1; i <= 3; i++) {
                PostEntity episode = published(author, "rtt-" + i, "RTT (" + i + ")", i);
                episode.assignToSeries(series.getId(), i);
                episode.updateTags(List.of("rtt-tag"));
                posts.save(episode);
                postIds.add(episode.getId());
                latestEpisodeId = episode.getId();
              }

              PostEntity draft = new PostEntity(author.getId(), "rtt-4", "RTT (4)", "ko");
              draft.assignToSeries(series.getId(), 4);
              posts.save(draft);
              postIds.add(draft.getId());

              PostEntity standalone = published(author, "standalone", "Standalone", 2);
              posts.save(standalone);
              postIds.add(standalone.getId());
              standaloneId = standalone.getId();
            });
  }

  private static PostEntity published(UserEntity author, String slug, String title, int day) {
    PostEntity post = new PostEntity(author.getId(), slug, title, "ko");
    post.publish();
    ReflectionTestUtils.setField(post, "publishedAt", BASE.plus(Duration.ofDays(day)));
    return post;
  }

  @AfterEach
  void removeCommittedFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction -> {
              postIds.forEach(id -> posts.findById(id).ifPresent(posts::delete));
              posts.flush();
              seriesRepository.findById(seriesId).ifPresent(seriesRepository::delete);
              users.deleteById(authorId);
            });
  }

  @ParameterizedTest(name = "{0} feed shows a series once, as its latest published episode")
  @ValueSource(strings = {"recent", "trending"})
  void browseFeedCollapsesSeriesToLatestEpisode(String sort) throws Exception {
    PublicFeedView feed = get("/api/v1/public/posts?sort=" + sort + "&size=20");

    assertThat(feed.items())
        .extracting(PublicFeedItem::id)
        .containsExactlyInAnyOrder(latestEpisodeId, standaloneId);
    PublicFeedItem episode =
        feed.items().stream().filter(i -> i.id() == latestEpisodeId).findFirst().orElseThrow();
    assertThat(episode.series()).isEqualTo(new FeedSeriesRef("rtt", "RTT 개선 기록", 3));
    PublicFeedItem standalone =
        feed.items().stream().filter(i -> i.id() == standaloneId).findFirst().orElseThrow();
    assertThat(standalone.series()).isNull();
    assertThat(feed.hasNext()).isFalse();
  }

  @Test
  void pagingCountsTheSeriesAsOneRow() throws Exception {
    assertThat(get("/api/v1/public/posts?size=1").hasNext()).isTrue();
    assertThat(get("/api/v1/public/posts?size=2").hasNext()).isFalse();
  }

  @Test
  void tagFeedKeepsEveryEpisodeWithItsSeries() throws Exception {
    PublicFeedView feed = get("/api/v1/public/posts?tag=rtt-tag&size=20");

    assertThat(feed.items()).hasSize(3);
    assertThat(feed.items())
        .extracting(PublicFeedItem::series)
        .containsOnly(new FeedSeriesRef("rtt", "RTT 개선 기록", 3));
  }

  private PublicFeedView get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("HTTP response: %s", response.body()).isEqualTo(200);
    return json.readValue(response.body(), PublicFeedView.class);
  }
}
