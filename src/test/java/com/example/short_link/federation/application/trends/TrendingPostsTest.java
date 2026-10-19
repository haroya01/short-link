package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.discovery.TrendingSpread;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class TrendingPostsTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
  private static final Duration PATIENCE = Duration.ofSeconds(4);
  private static final TrendsProperties PROPS =
      new TrendsProperties(
          true, List.of(" A.example ", "b.example", "c.example", ""), 3, null, PATIENCE);

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private RemoteAccountStandingReader standings;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private TrendingPosts trending;

  @BeforeEach
  void trending() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(serverBlocks.severityFor(anyString())).thenReturn(Optional.empty());
    lenient().when(standings.idsOf(any())).thenReturn(Map.of());
    trending =
        new TrendingPosts(
            PROPS,
            new TrendingSpread(2),
            http,
            signingKeys,
            serverBlocks,
            standings,
            new TrendingStatusReader(
                new RemoteNoteParser(
                    new FederationUrls(
                        new FederationProperties("https://kurl.me", "https://blog.kurl.me"))),
                JSON),
            JSON,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static URI trendsOf(String server) {
    return URI.create("https://" + server + "/api/v1/trends/statuses?limit=3");
  }

  private static String post(String host, int n) {
    return "https://" + host + "/users/u" + n + "/statuses/" + n;
  }

  private static ObjectNode status(String uri, long boosts, long likes) {
    String host = URI.create(uri).getHost();
    String name = uri.substring(uri.indexOf("/users/") + 7, uri.indexOf("/statuses/"));
    ObjectNode status = TrendingStatusReaderTest.status(uri, name + "@" + host);
    status.put("reblogs_count", boosts);
    status.put("favourites_count", likes);
    return status;
  }

  private void answers(String server, ObjectNode... statuses) {
    ArrayNode array = JSON.createArrayNode();
    for (ObjectNode status : statuses) {
      array.add(status);
    }
    when(http.getJson(trendsOf(server), instance, PATIENCE))
        .thenReturn(
            new FederationHttp.Result.Ok(
                200, JSON.writeValueAsString(array).getBytes(StandardCharsets.UTF_8)));
  }

  private List<String> uris(List<TrendingNote> notes) {
    return notes.stream().map(TrendingNote::uri).toList();
  }

  @Test
  void eachServersFirstPostComesBeforeAnySecondAndAPostOnSeveralServersIsShownOnce() {
    answers(
        "a.example",
        status(post("x.example", 1), 100, 100),
        status(post("x.example", 2), 90, 0),
        status(post("y.example", 3), 0, 0));
    answers(
        "b.example",
        status(post("z.example", 4), 1, 1),
        status(post("x.example", 1), 500, 3),
        status(post("z.example", 5), 1000, 1000));
    answers("c.example", status(post("x.example", 2), 5, 7), status(post("w.example", 6), 0, 0));

    trending.refresh();
    List<TrendingNote> notes = trending.list(null, 20);

    assertThat(uris(notes))
        .containsExactly(
            post("x.example", 1),
            post("x.example", 2),
            post("z.example", 4),
            post("w.example", 6),
            post("z.example", 5),
            post("y.example", 3));
    assertThat(notes.get(0).servers()).containsExactly("a.example", "b.example");
    assertThat(notes.get(0).repostCount()).isEqualTo(500);
    assertThat(notes.get(0).likeCount()).isEqualTo(100);
    assertThat(notes.get(1).servers()).containsExactly("a.example", "c.example");
    assertThat(notes.get(1).likeCount()).isEqualTo(7);
    assertThat(notes.get(2).servers()).containsExactly("b.example");
    InOrder order = Mockito.inOrder(http);
    order.verify(http).getJson(trendsOf("a.example"), instance, PATIENCE);
    order.verify(http).getJson(trendsOf("b.example"), instance, PATIENCE);
    order.verify(http).getJson(trendsOf("c.example"), instance, PATIENCE);
    verify(signingKeys, times(1)).forInstance();
    verify(standings, never()).hidden(any(), any(), any(), any());
  }

  @Test
  void anAccountPlacesTwoPostsPerRoundSoAThirdWaitsForTheOthers() {
    String mina = "https://x.example/users/mina/statuses/";
    answers("a.example", status(mina + 1, 0, 0), status(mina + 2, 0, 0), status(mina + 3, 0, 0));
    answers(
        "b.example",
        status(mina + 4, 0, 0),
        status(mina + 5, 0, 0),
        status(post("z.example", 6), 0, 0));

    trending.refresh();

    assertThat(uris(trending.list(null, 20)))
        .containsExactly(mina + 1, mina + 4, post("z.example", 6), mina + 2, mina + 5, mina + 3);
    assertThat(uris(trending.list(null, 3)))
        .containsExactly(mina + 1, mina + 4, post("z.example", 6));
  }

  @Test
  void aServerThatDoesNotAnswerIsLeftOutAndAskedAgainAfterLongerAndLongerWaits() {
    when(http.getJson(trendsOf("a.example"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(429, "HTTP 429"));
    when(http.getJson(trendsOf("b.example"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Unreachable("SocketTimeoutException"));
    answers("c.example", status(post("x.example", 1), 0, 0));

    for (int refresh = 0; refresh < 32; refresh++) {
      trending.refresh();
    }

    verify(http, times(6)).getJson(trendsOf("a.example"), instance, PATIENCE);
    verify(http, times(32)).getJson(trendsOf("c.example"), instance, PATIENCE);
    assertThat(uris(trending.list(null, 20))).containsExactly(post("x.example", 1));

    answers("a.example", status(post("y.example", 2), 0, 0));
    for (int refresh = 0; refresh < 5; refresh++) {
      trending.refresh();
    }
    verify(http, times(6)).getJson(trendsOf("a.example"), instance, PATIENCE);
    trending.refresh();
    trending.refresh();
    verify(http, times(8)).getJson(trendsOf("a.example"), instance, PATIENCE);
    assertThat(uris(trending.list(null, 20)))
        .containsExactly(post("y.example", 2), post("x.example", 1));
  }

  @Test
  void anAnswerThatIsNotAListOfPostsCountsAsNoAnswer() {
    when(http.getJson(trendsOf("a.example"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Ok(200, "{\"error\":\"x\"}".getBytes()));
    when(http.getJson(trendsOf("b.example"), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Ok(200, "<html>".getBytes()));
    answers("c.example", status(post("x.example", 1), 0, 0));

    trending.refresh();
    trending.refresh();

    verify(http, times(1)).getJson(trendsOf("a.example"), instance, PATIENCE);
    verify(http, times(1)).getJson(trendsOf("b.example"), instance, PATIENCE);
    verify(http, times(2)).getJson(trendsOf("c.example"), instance, PATIENCE);
  }

  @Test
  void aServerBlockedHereIsNotAskedAndPostsFromOneAreDropped() {
    when(serverBlocks.severityFor("b.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));
    when(serverBlocks.severityFor("y.example")).thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    answers(
        "a.example",
        status(post("x.example", 1), 0, 0),
        status(post("y.example", 2), 0, 0),
        status(post("x.example", 3), 0, 0));
    answers("c.example", status(post("y.example", 4), 0, 0), status(post("w.example", 5), 0, 0));

    trending.refresh();

    assertThat(uris(trending.list(null, 20)))
        .containsExactly(post("x.example", 1), post("w.example", 5), post("x.example", 3));
    verify(http, never()).getJson(trendsOf("b.example"), instance, PATIENCE);
    verify(serverBlocks, times(1)).severityFor("x.example");
    verify(serverBlocks, times(1)).severityFor("y.example");
  }

  @Test
  void nothingToAskNeedsNoKey() {
    when(serverBlocks.severityFor(anyString()))
        .thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));

    trending.refresh();

    assertThat(trending.list(null, 20)).isEmpty();
    verify(signingKeys, never()).forInstance();
    verify(http, never()).getJson(any(), any(), any());
  }

  @Test
  void aMemberDoesNotSeeAccountsOrServersTheyBlockedOrMutedAndTheLimitIsKept() {
    answers(
        "a.example",
        status(post("x.example", 1), 0, 0),
        status(post("y.example", 2), 0, 0),
        status(post("x.example", 3), 0, 0));
    trending.refresh();
    when(standings.hidden(
            7L,
            List.of(
                "https://x.example/users/u1",
                "https://y.example/users/u2",
                "https://x.example/users/u3"),
            List.of("x.example", "y.example"),
            NOW))
        .thenReturn(
            new RemoteAccountStandingReader.Hidden(
                Set.of("https://x.example/users/u1"), Set.of("y.example")));

    assertThat(uris(trending.list(7L, 20))).containsExactly(post("x.example", 3));
    assertThat(uris(trending.list(null, 0))).containsExactly(post("x.example", 1));
    assertThat(trending.list(null, 1000)).hasSize(3);
    verify(standings, times(1)).hidden(any(), any(), any(), any());
  }

  @Test
  void anItemIsANoteWithoutAnIdOrReaderMarksAndAKnownAuthorCarriesTheirIdHere() {
    ObjectNode question = status(post("x.example", 1), 0, 0);
    question.set(
        "poll",
        JSON.readTree(
            """
            {"expires_at":null,"expired":false,"multiple":false,"voters_count":3,
             "options":[{"title":"a","votes_count":1},{"title":"b","votes_count":2}]}"""));
    answers("a.example", question, status(post("y.example", 2), 0, 0));
    when(standings.idsOf(List.of("https://x.example/users/u1", "https://y.example/users/u2")))
        .thenReturn(Map.of("https://x.example/users/u1", 41L));
    when(standings.hidden(any(), any(), any(), any()))
        .thenReturn(RemoteAccountStandingReader.Hidden.NONE);

    trending.refresh();
    List<TrendingNote> anyone = trending.list(null, 20);
    List<TrendingNote> member = trending.list(7L, 20);

    assertThat(anyone.get(0).author().id()).isEqualTo(-41L);
    assertThat(anyone.get(0).author().remoteId()).isEqualTo(41L);
    assertThat(anyone.get(1).author().id()).isNull();
    assertThat(anyone.get(1).author().remoteId()).isNull();
    assertThat(anyone.get(0).poll().voted()).isNull();
    assertThat(anyone.get(0).poll().ownVotes()).isNull();
    assertThat(member.get(0).poll().voted()).isTrue();
    assertThat(member.get(0).poll().ownVotes()).isEmpty();
    assertThat(member.get(1).poll()).isNull();

    JsonNode item = JSON.valueToTree(anyone.get(0));
    List<String> fields = new ArrayList<>(item.propertyNames());
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "id",
            "body",
            "createdAt",
            "editedAt",
            "likeCount",
            "likedByMe",
            "author",
            "media",
            "quotedPost",
            "inReplyToId",
            "replyCount",
            "repostCount",
            "repostedByMe",
            "quotedNote",
            "linkPreview",
            "repostedBy",
            "quoteCount",
            "bookmarkedByMe",
            "mentions",
            "contentWarning",
            "sensitive",
            "pinned",
            "visibility",
            "poll",
            "conversationMuted",
            "language",
            "thread",
            "replyPolicy",
            "canReply",
            "hidden",
            "remote",
            "uri",
            "url",
            "sourceHost",
            "servers");
    assertThat(item.get("id").isNull()).isTrue();
    assertThat(item.get("likedByMe").isNull()).isTrue();
    assertThat(item.get("mentions").isEmpty()).isTrue();
    assertThat(item.get("visibility").asString()).isEqualTo("public");
    assertThat(item.get("replyPolicy").asString()).isEqualTo("everyone");
    assertThat(item.get("sourceHost").asString()).isEqualTo("a.example");
    assertThat(new ArrayList<>(item.get("author").propertyNames()))
        .containsExactlyInAnyOrder("id", "username", "avatarUrl", "displayName", "remoteId", "url");
    assertThat(new ArrayList<>(item.get("poll").propertyNames()))
        .containsExactlyInAnyOrder(
            "expiresAt",
            "expired",
            "multiple",
            "votesCount",
            "votersCount",
            "options",
            "voted",
            "ownVotes");
  }

  @Test
  void anEmptyListAsksNothingForAMember() {
    assertThat(trending.list(7L, 20)).isEmpty();
    verify(standings, never()).hidden(any(), any(), any(), any());
  }

  @Test
  void aServerBlockedNowLeavesAtOnceWithItsSubdomains() {
    answers(
        "a.example",
        status(post("x.example", 1), 0, 0),
        status(post("social.x.example", 2), 0, 0),
        status(post("notx.example", 3), 0, 0));
    trending.refresh();

    trending.withdraw("x.example");

    assertThat(uris(trending.list(null, 20))).containsExactly(post("notx.example", 3));
  }

  @Test
  void theSettingsKeepServersAsTypedOnceAndAskAtMostWhatMastodonAnswers() {
    assertThat(PROPS.hosts()).containsExactly("a.example", "b.example", "c.example");
    TrendsProperties defaults = new TrendsProperties(false, null, 0, null, null);
    assertThat(defaults.hosts()).isEmpty();
    assertThat(defaults.perHost()).isEqualTo(20);
    assertThat(defaults.every()).isEqualTo(Duration.ofMinutes(30));
    assertThat(defaults.patience()).isEqualTo(Duration.ofSeconds(5));
    assertThat(new TrendsProperties(true, List.of(), 500, null, null).perHost()).isEqualTo(40);
  }
}
