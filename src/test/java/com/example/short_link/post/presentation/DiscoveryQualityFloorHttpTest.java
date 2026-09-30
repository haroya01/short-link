package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 발견 화면(최신·인기·태그·태그 칩·추천 작가)은 본문이 하한선 아래인 글을 올리지 않는다. 작가 페이지와 검색은 그대로 둔다.
class DiscoveryQualityFloorHttpTest extends DockerHttpTest {

  private static final Instant BASE = Instant.parse("2026-09-01T00:00:00Z");
  private static final String TAG = "floor-topic";

  @LocalServerPort private int port;
  @Autowired private PostRepository posts;
  @Autowired private PostSearchTextRepository searchText;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final List<Long> postIds = new ArrayList<>();
  private Long authorId;
  private Long realId;
  private Long junkId;

  @BeforeEach
  void commitOneRealPostAndOneNewerTestPost() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              UserEntity author = new UserEntity("floor-author@x.com", "google", "g-floor-author");
              author.claimUsername("floor-author");
              users.save(author);
              authorId = author.getId();

              PostEntity real = published(author, "measured", "측정 기록", 1);
              DiscoverableBodies.discoverable(real);
              posts.save(real);
              realId = real.getId();

              PostEntity junk = published(author, "junk", "거거거구ㅜㅅ", 2);
              junk.measureBody("ㅎㅎㅎ");
              posts.save(junk);
              junkId = junk.getId();
              searchText.upsert(junkId, "거거거구ㅜㅅ ㅎㅎㅎ");

              postIds.addAll(List.of(realId, junkId));
            });
  }

  private static PostEntity published(UserEntity author, String slug, String title, int day) {
    PostEntity post = new PostEntity(author.getId(), slug, title, "ko");
    post.updateTags(List.of(TAG));
    post.publish();
    ReflectionTestUtils.setField(post, "publishedAt", BASE.plus(Duration.ofDays(day)));
    return post;
  }

  @AfterEach
  void removeCommittedFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              postIds.forEach(id -> posts.findById(id).ifPresent(posts::delete));
              posts.flush();
              users.deleteById(authorId);
            });
  }

  @ParameterizedTest(name = "{0} feed leaves out the post below the floor")
  @ValueSource(strings = {"recent", "trending"})
  void browseFeedsLeaveOutThinPosts(String sort) throws Exception {
    List<Long> ids = feedIds(get("/api/v1/public/posts?sort=" + sort + "&size=50"));
    assertThat(ids).contains(realId).doesNotContain(junkId);
  }

  @Test
  void tagFeedAndTagChipCountOnlyDiscoverablePosts() throws Exception {
    JsonNode tagFeed = get("/api/v1/public/posts?tag=" + TAG + "&size=20");
    assertThat(feedIds(tagFeed)).containsExactly(realId);
    assertThat(tagFeed.path("hasNext").asBoolean()).isFalse();

    JsonNode tags = get("/api/v1/public/tags?limit=100");
    long chip = 0;
    for (JsonNode tag : tags) {
      if (TAG.equals(tag.path("tag").asString())) chip = tag.path("count").asLong();
    }
    assertThat(chip).isEqualTo(1);
  }

  @Test
  void theAuthorPageAndSearchStillShowTheThinPost() throws Exception {
    JsonNode page = get("/api/v1/public/profiles/floor-author/posts");
    List<Long> onPage = new ArrayList<>();
    page.path("posts").forEach(p -> onPage.add(p.path("id").asLong()));
    assertThat(onPage).contains(realId, junkId);

    String q = URLEncoder.encode("거거거구", StandardCharsets.UTF_8);
    assertThat(feedIds(get("/api/v1/public/posts?q=" + q + "&size=20"))).contains(junkId);
  }

  private static List<Long> feedIds(JsonNode feed) {
    List<Long> ids = new ArrayList<>();
    feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  private JsonNode get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("HTTP response: %s", response.body()).isEqualTo(200);
    return json.readTree(response.body());
  }
}
