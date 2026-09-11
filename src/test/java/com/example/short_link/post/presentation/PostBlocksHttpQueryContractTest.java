package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.presentation.request.CreatePostRequest;
import com.example.short_link.post.presentation.request.ReplaceBlocksRequest;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.testsupport.HttpQueryContracts;
import com.example.short_link.testsupport.HttpQueryContracts.CapturedRequest;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.core.interceptor.QueryInterceptor;
import io.queryaudit.core.model.QueryRecord;
import io.queryaudit.core.parser.SqlParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the authenticated HTTP write/read paths and verifies their committed database results.
 */
class PostBlocksHttpQueryContractTest extends DockerHttpTest {

  @LocalServerPort private int port;
  @Autowired private PostRepository posts;
  @Autowired private PostBlockRepository blocks;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private QueryInterceptor interceptor;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private TransactionTemplate transactions;
  private HttpQueryContracts contracts;
  private Long authorId;
  private String accessToken;

  @BeforeEach
  void commitAuthorAndMintAccessToken() {
    transactions = new TransactionTemplate(transactionManager);
    contracts = new HttpQueryContracts(interceptor);
    authorId =
        transactions.execute(
            transaction -> {
              UserEntity author =
                  new UserEntity("http-block-author@example.com", "google", "http-block-author");
              author.claimUsername("http-block-author");
              return users.save(author).getId();
            });
    accessToken = jwt.createAccessToken(authorId, "USER");
  }

  @AfterEach
  void removeOnlyThisAuthorsCommittedData() {
    if (authorId == null) return;
    transactions.executeWithoutResult(
        transaction -> {
          // Query by the fixture owner so a failed HTTP assertion cannot leak a newly created post.
          for (PostEntity post : posts.findAllByUserIdOrderByCreatedAtDesc(authorId)) {
            blocks.deleteAllByPostId(post.getId());
            posts.delete(post);
          }
          posts.flush();
          users.deleteById(authorId);
        });
  }

  @Test
  void createsAndCommitsADraftThroughAuthenticatedHttp() throws Exception {
    var captured =
        sendJson(
            "post-create",
            "POST",
            "/api/v1/posts",
            new CreatePostRequest("http-created", "Created over HTTP", "ko"));

    assertStatus(captured, 201);
    PostView response = json.readValue(captured.response().body(), PostView.class);
    PostEntity committed =
        transactions.execute(transaction -> posts.findById(response.id()).orElseThrow());
    assertThat(committed.getUserId()).isEqualTo(authorId);
    assertThat(committed.getSlug()).isEqualTo("http-created");
    assertThat(committed.getTitle()).isEqualTo("Created over HTTP");
    assertThat(committed.getLanguageTag()).isEqualTo("ko");
    assertThat(committed.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(response.slug()).isEqualTo(committed.getSlug());
    assertThat(response.title()).isEqualTo(committed.getTitle());
    assertThat(response.status()).isEqualTo(committed.getStatus().name());
    contracts.verify(captured);
  }

  @Test
  void replacesTwoBlocksWithSixInOneInsertAndReturnsCommittedIds() throws Exception {
    PostFixture fixture = commitPostWithBlocks(List.of("old first", "old second"));
    List<String> contents = paragraphContents("replacement", 6);
    ReplaceBlocksRequest request =
        new ReplaceBlocksRequest(
            contents.stream()
                .map(content -> new ReplaceBlocksRequest.BlockItem("PARAGRAPH", content))
                .toList());

    var captured =
        sendJson("post-blocks-replace-six", "PUT", blocksPath(fixture.postId()), request);

    List<PostBlockView> response = readBlockResponse(captured);
    List<PostBlockView> committed = committedBlocks(fixture.postId());
    assertThat(committed).hasSize(6);
    assertThat(committed).extracting(PostBlockView::blockOrder).containsExactly(0, 1, 2, 3, 4, 5);
    assertThat(committed).extracting(PostBlockView::content).containsExactlyElementsOf(contents);
    assertThat(committed).extracting(PostBlockView::type).containsOnly("PARAGRAPH");
    assertThat(committed)
        .extracting(PostBlockView::id)
        .doesNotContainNull()
        .doesNotHaveDuplicates();
    assertThat(response).containsExactlyElementsOf(committed);
    String searchText =
        jdbc.queryForObject(
            "SELECT search_text FROM post_search_text WHERE post_id = ?",
            String.class,
            fixture.postId());
    assertThat(searchText).contains(contents).doesNotContain("old first", "old second");
    for (PostBlockView oldBlock : fixture.blocks()) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM post_block WHERE id = ?", Long.class, oldBlock.id()))
          .isZero();
    }
    assertOneBlockInsertAfterDelete(captured);
    contracts.verify(captured);
  }

  @Test
  void readsSixCommittedBlocksThroughAuthenticatedHttp() throws Exception {
    PostFixture fixture = commitPostWithBlocks(paragraphContents("read", 6));

    var captured =
        capture("post-blocks-read", authorizedRequest(blocksPath(fixture.postId())).GET().build());

    List<PostBlockView> response = readBlockResponse(captured);
    List<PostBlockView> committed = committedBlocks(fixture.postId());
    assertThat(committed).hasSize(6).containsExactlyElementsOf(fixture.blocks());
    assertThat(response).containsExactlyElementsOf(committed);
    assertThat(captured.counts().insertCount()).isZero();
    assertThat(captured.counts().updateCount()).isZero();
    assertThat(captured.counts().deleteCount()).isZero();
    contracts.verify(captured);
  }

  private PostFixture commitPostWithBlocks(List<String> contents) {
    long postId =
        transactions.execute(
            transaction -> {
              PostEntity post =
                  posts.save(new PostEntity(authorId, "http-blocks", "HTTP blocks", "ko"));
              blocks.insertAll(
                  IntStream.range(0, contents.size())
                      .mapToObj(
                          order ->
                              new PostBlockEntity(
                                  post.getId(),
                                  PostBlockType.PARAGRAPH,
                                  contents.get(order),
                                  order))
                      .toList());
              return post.getId();
            });
    return new PostFixture(postId, committedBlocks(postId));
  }

  private List<PostBlockView> committedBlocks(long postId) {
    return jdbc.query(
        "SELECT id, block_type, content, block_order FROM post_block WHERE post_id = ? ORDER BY block_order",
        (row, index) ->
            new PostBlockView(
                row.getLong("id"),
                row.getString("block_type"),
                row.getString("content"),
                row.getInt("block_order")),
        postId);
  }

  private CapturedRequest<HttpResponse<String>> sendJson(
      String contractId, String method, String path, Object body) throws Exception {
    HttpRequest request =
        authorizedRequest(path)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return capture(contractId, request);
  }

  private CapturedRequest<HttpResponse<String>> capture(String contractId, HttpRequest request)
      throws Exception {
    HttpRequest traced =
        HttpRequest.newBuilder(request, (name, value) -> true)
            .header("X-Query-Contract-ID", contractId)
            .build();
    return contracts.capture(
        contractId, () -> http.send(traced, HttpResponse.BodyHandlers.ofString()));
  }

  private HttpRequest.Builder authorizedRequest(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Authorization", "Bearer " + accessToken)
        .timeout(Duration.ofSeconds(30));
  }

  private List<PostBlockView> readBlockResponse(CapturedRequest<HttpResponse<String>> captured) {
    assertStatus(captured, 200);
    return Arrays.asList(json.readValue(captured.response().body(), PostBlockView[].class));
  }

  private void assertStatus(CapturedRequest<HttpResponse<String>> captured, int expectedStatus) {
    assertThat(captured.response().statusCode())
        .as("HTTP response: %s", captured.response().body())
        .isEqualTo(expectedStatus);
    assertThat(captured.queries()).isNotEmpty();
  }

  private void assertOneBlockInsertAfterDelete(CapturedRequest<?> captured) {
    List<String> sql = captured.queries().stream().map(QueryRecord::sql).toList();
    List<String> inserts =
        sql.stream()
            .filter(SqlParser::isInsertQuery)
            .filter(statement -> "post_block".equals(SqlParser.extractInsertTable(statement)))
            .toList();
    List<String> deletes =
        sql.stream()
            .filter(SqlParser::isDeleteQuery)
            .filter(statement -> SqlParser.extractTableNames(statement).contains("post_block"))
            .toList();
    assertThat(inserts).as("All six blocks must use one post_block INSERT").hasSize(1);
    assertThat(deletes).as("The previous body must be deleted once").hasSize(1);
    assertThat(sql.indexOf(deletes.getFirst())).isLessThan(sql.indexOf(inserts.getFirst()));
  }

  private static List<String> paragraphContents(String prefix, int count) {
    return IntStream.range(0, count).mapToObj(index -> prefix + " paragraph " + index).toList();
  }

  private static String blocksPath(long postId) {
    return "/api/v1/posts/" + postId + "/blocks";
  }

  private record PostFixture(long postId, List<PostBlockView> blocks) {}
}
