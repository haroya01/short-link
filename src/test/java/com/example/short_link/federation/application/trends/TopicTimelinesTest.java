package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.trends.TopicFixtures.JSON;
import static com.example.short_link.federation.application.trends.TopicFixtures.ok;
import static com.example.short_link.federation.application.trends.TopicFixtures.status;
import static com.example.short_link.federation.application.trends.TopicFixtures.uri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.topics.Topic;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class TopicTimelinesTest {

  private static final Instant AT = Instant.parse("2026-11-18T08:00:00Z");
  private static final Duration PATIENCE = Duration.ofSeconds(4);

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private RemoteAccountStandingReader standings;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private TopicTimelines timelines;

  @BeforeEach
  void timelines() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(serverBlocks.severityFor(anyString())).thenReturn(Optional.empty());
    lenient().when(standings.idsOf(any())).thenReturn(Map.of());
    timelines =
        new TopicTimelines(
            new TrendsProperties(true, List.of("a.example"), 0, 0, null, PATIENCE),
            http,
            signingKeys,
            serverBlocks,
            standings,
            TopicFixtures.READER,
            JSON);
  }

  private static URI tagTimeline(String host, String encodedTag, int limit) {
    return URI.create(
        "https://" + host + "/api/v1/timelines/tag/" + encodedTag + "?limit=" + limit);
  }

  private void answers(URI timeline, ObjectNode... statuses) {
    when(http.getJson(timeline, instance, PATIENCE))
        .thenReturn(ok(JSON.writeValueAsString(List.of(statuses))));
  }

  private static List<String> uris(List<TrendingStatusReader.Read> reads) {
    return reads.stream().map(read -> read.note().uri()).toList();
  }

  @Test
  void aTagIsAskedByItsNameAndALinkByItsAddressBothEncoded() {
    assertThat(TopicTimelines.timeline("a.example", TopicFixtures.tag("소풍 a/b"), 40))
        .hasToString("https://a.example/api/v1/timelines/tag/%EC%86%8C%ED%92%8D%20a%2Fb?limit=40");
    assertThat(
            TopicTimelines.timeline(
                "a.example", TopicFixtures.link("https://news.example/a?id=1&x=봄#top"), 20))
        .hasToString(
            "https://a.example/api/v1/timelines/link"
                + "?url=https%3A%2F%2Fnews.example%2Fa%3Fid%3D1%26x%3D%EB%B4%84%23top&limit=20");
  }

  @Test
  void atMostThreeServersAreAskedFortyPostsEachAndAPostOnTwoIsShownOnce() {
    Topic harbor = TopicFixtures.tag("harbor");
    String shared = uri("x.example", "ann", 1);
    answers(
        tagTimeline("a.example", "harbor", 40),
        status(shared, AT, "en").put("favourites_count", 2),
        status(uri("a.example", "bo", 2), AT.minusSeconds(60), "en"));
    answers(
        tagTimeline("b.example", "harbor", 40),
        status(shared, AT, "en").put("favourites_count", 9));
    answers(tagTimeline("c.example", "harbor", 40), status(uri("c.example", "cy", 3), AT, "en"));

    List<TrendingStatusReader.Read> reads =
        timelines.posts(harbor, List.of("a.example", "b.example", "c.example", "d.example"));

    assertThat(uris(reads))
        .containsExactlyInAnyOrder(shared, uri("a.example", "bo", 2), uri("c.example", "cy", 3));
    TrendingNote both =
        reads.stream()
            .filter(read -> read.note().uri().equals(shared))
            .findFirst()
            .orElseThrow()
            .note();
    assertThat(both.servers()).containsExactly("a.example", "b.example");
    assertThat(both.likeCount()).isEqualTo(9);
    verify(http, never()).getJson(eq(tagTimeline("d.example", "harbor", 40)), any(), any());
    verify(signingKeys, times(1)).forInstance();
  }

  @Test
  void aServerThatFailsTimesOutOrBreaksContributesNothing() {
    Topic harbor = TopicFixtures.tag("harbor");
    when(http.getJson(tagTimeline("a.example", "harbor", 40), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Unreachable("timed out"));
    when(http.getJson(tagTimeline("b.example", "harbor", 40), instance, PATIENCE))
        .thenThrow(new IllegalStateException("boom"));
    answers(tagTimeline("c.example", "harbor", 40), status(uri("c.example", "cy", 3), AT, "en"));

    List<TrendingStatusReader.Read> reads =
        timelines.posts(harbor, List.of("a.example", "b.example", "c.example"));

    assertThat(uris(reads)).containsExactly(uri("c.example", "cy", 3));
  }

  @Test
  void anAnswerThatIsNotAListOfPostsContributesNothing() {
    Topic harbor = TopicFixtures.tag("harbor");
    when(http.getJson(tagTimeline("a.example", "harbor", 40), instance, PATIENCE))
        .thenReturn(ok("{\"error\":\"nope\"}"));
    when(http.getJson(tagTimeline("b.example", "harbor", 40), instance, PATIENCE))
        .thenReturn(ok("<html>"));
    when(http.getJson(tagTimeline("c.example", "harbor", 40), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Failed(404, "Not Found"));
    when(http.getJson(tagTimeline("a.example", "harbor", 20), instance, PATIENCE))
        .thenReturn(new FederationHttp.Result.Refused("not a public address"));

    assertThat(timelines.posts(harbor, List.of("a.example", "b.example", "c.example"))).isEmpty();
    assertThat(timelines.sample(harbor, "a.example", instance)).isEmpty();
  }

  @Test
  void aTopicWithNoServerElsewhereAsksNobody() {
    assertThat(timelines.posts(TopicFixtures.tag("harbor"), List.of())).isEmpty();

    verifyNoInteractions(http, signingKeys);
  }

  @Test
  void postsGoThroughTheConsentFiltersTheServerBlocksAndTheKnownAccounts() {
    Topic harbor = TopicFixtures.tag("harbor");
    ObjectNode reply = status(uri("a.example", "re", 5), AT, "en");
    reply.put("in_reply_to_id", "4");
    ObjectNode hidden = status(uri("a.example", "shy", 6), AT, "en");
    ((ObjectNode) hidden.path("account")).put("discoverable", false);
    answers(
        tagTimeline("a.example", "harbor", 40),
        status(uri("a.example", "ann", 1), AT, "en"),
        status(uri("bad.example", "mal", 2), AT, "en"),
        reply,
        hidden);
    when(serverBlocks.severityFor("bad.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.LIMIT));
    when(standings.idsOf(List.of("https://a.example/users/ann")))
        .thenReturn(Map.of("https://a.example/users/ann", 77L));

    List<TrendingStatusReader.Read> reads = timelines.posts(harbor, List.of("a.example"));

    assertThat(uris(reads)).containsExactly(uri("a.example", "ann", 1));
    assertThat(reads.getFirst().note().author().remoteId()).isEqualTo(77L);
  }

  @Test
  void aSampleIsOnePageOfTwentyThatNamesItsLanguageFromEveryPostAndKeepsTheReadableOnes() {
    Topic harbor = TopicFixtures.tag("harbor");
    ObjectNode reply = status(uri("a.example", "re", 5), AT, "ja");
    reply.put("in_reply_to_id", "4");
    answers(
        tagTimeline("a.example", "harbor", 20),
        status(uri("a.example", "ann", 1), AT, "ja"),
        status(uri("a.example", "bo", 2), AT, null),
        status(uri("a.example", "cy", 3), AT, "en"),
        reply);

    TopicTimelines.Sample sample = timelines.sample(harbor, "a.example", instance).orElseThrow();

    assertThat(sample.language()).isEqualTo("ja");
    assertThat(uris(sample.reads()))
        .containsExactlyInAnyOrder(
            uri("a.example", "ann", 1), uri("a.example", "bo", 2), uri("a.example", "cy", 3));
  }
}
