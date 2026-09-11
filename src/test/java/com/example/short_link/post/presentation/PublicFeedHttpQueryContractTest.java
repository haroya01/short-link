package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.testsupport.HttpQueryContracts;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.core.interceptor.QueryInterceptor;
import io.queryaudit.core.model.QueryRecord;
import io.queryaudit.core.parser.SqlParser;
import io.queryaudit.core.regression.QueryContracts;
import io.queryaudit.core.regression.QueryCountBaseline;
import io.queryaudit.core.regression.QueryCounts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The author and tag query budget must stay constant as the HTTP feed page grows. */
class PublicFeedHttpQueryContractTest extends DockerHttpTest {

  private static final int FIXTURE_POST_COUNT = 20;

  @LocalServerPort private int port;
  @Autowired private PostRepository posts;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private QueryInterceptor interceptor;
  @Autowired private JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private List<FeedPost> fixtures = List.of();

  @BeforeEach
  void commitPublishedPostsWithDistinctAuthorsAndTags() {
    fixtures =
        new TransactionTemplate(transactionManager)
            .execute(
                transaction -> {
                  List<FeedPost> created = new ArrayList<>();
                  for (int i = 0; i < FIXTURE_POST_COUNT; i++) {
                    String username = "feed-author-" + i;
                    UserEntity author =
                        new UserEntity(username + "@example.com", "google", username);
                    author.claimUsername(username);
                    users.save(author);

                    List<String> tags = List.of("topic-" + i, "shared-topic");
                    PostEntity post =
                        new PostEntity(author.getId(), "feed-post-" + i, "Feed post " + i, "ko");
                    post.updateTags(tags);
                    post.publish();
                    posts.save(post);
                    created.add(new FeedPost(post.getId(), author.getId(), username, tags));
                  }
                  return List.copyOf(created);
                });

    // This query runs after commit and outside the HTTP capture window.
    assertThat(posts.countPublished(null)).isEqualTo(FIXTURE_POST_COUNT);
  }

  @AfterEach
  void removeCommittedFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction -> {
              for (FeedPost fixture : fixtures) {
                posts.findById(fixture.postId()).ifPresent(posts::delete);
              }
              posts.flush();
              fixtures.forEach(fixture -> users.deleteById(fixture.authorId()));
            });
  }

  @ParameterizedTest(name = "recent feed size {0} keeps one author batch and one tag batch")
  @ValueSource(ints = {1, 8, 20})
  void readsCommittedFeedAcrossHttpWithinTheQueryContract(int size) throws Exception {
    HttpQueryContracts contracts = new HttpQueryContracts(interceptor);
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/public/posts?size=" + size))
            .timeout(Duration.ofSeconds(30))
            .header("X-Query-Contract-ID", "feed-recent-size-" + size)
            .GET()
            .build();

    var captured =
        contracts.capture(
            "feed-recent-size-" + size,
            () -> http.send(request, HttpResponse.BodyHandlers.ofString()));

    assertThat(captured.response().statusCode())
        .as("HTTP response: %s", captured.response().body())
        .isEqualTo(200);
    PublicFeedView feed = json.readValue(captured.response().body(), PublicFeedView.class);
    assertThat(feed.items()).hasSize(size);
    assertThat(feed.page()).isZero();
    assertThat(feed.size()).isEqualTo(size);
    assertThat(feed.hasNext()).isEqualTo(size < FIXTURE_POST_COUNT);
    assertThat(feed.items()).extracting(PublicFeedItem::id).doesNotHaveDuplicates();
    assertPersistedAuthorsAndTags(feed);

    assertThat(captured.queries()).isNotEmpty();
    assertThat(selectsFrom(captured, "posts"))
        .as("The feed must read persisted posts")
        .isNotEmpty();
    assertThat(selectsFrom(captured, "users"))
        .as("All %s authors must be loaded by one SELECT", size)
        .hasSize(1);
    assertThat(selectsFrom(captured, "post_tag"))
        .as("Tags for all %s posts must be loaded by one SELECT", size)
        .hasSize(1);
    assertThat(captured.counts().insertCount()).isZero();
    assertThat(captured.counts().updateCount()).isZero();
    assertThat(captured.counts().deleteCount()).isZero();

    if (size == 1) {
      assertZeroQueryContractRejectsTheActualHttpCapture(captured);
    }
    contracts.verify(captured);
  }

  private static List<String> selectsFrom(
      HttpQueryContracts.CapturedRequest<?> captured, String table) {
    return captured.queries().stream()
        .map(QueryRecord::sql)
        .filter(SqlParser::isSelectQuery)
        .filter(sql -> SqlParser.extractTableNames(sql).stream().anyMatch(table::equalsIgnoreCase))
        .toList();
  }

  private void assertPersistedAuthorsAndTags(PublicFeedView feed) {
    Map<Long, FeedPost> expectedById =
        fixtures.stream().collect(Collectors.toMap(FeedPost::postId, Function.identity()));
    for (PublicFeedItem item : feed.items()) {
      assertThat(expectedById).containsKey(item.id());
      FeedPost expected = expectedById.get(item.id());
      assertThat(item.author().id()).isEqualTo(expected.authorId());
      assertThat(item.author().username()).isEqualTo(expected.username());
      assertThat(item.tags()).containsExactlyElementsOf(expected.tags());
    }
  }

  private void assertZeroQueryContractRejectsTheActualHttpCapture(
      HttpQueryContracts.CapturedRequest<?> captured) {
    String id = "feed-negative-control";
    String failure =
        QueryContracts.verify(
            id,
            "HTTP",
            id,
            captured.counts(),
            Map.of(QueryCountBaseline.key(id), new QueryCounts(0, 0, 0, 0, 0)),
            captured.queries());
    assertThat(failure)
        .as("A zero-query contract must reject the real HTTP request's database queries")
        .isNotNull();
  }

  private record FeedPost(long postId, long authorId, String username, List<String> tags) {}
}
